package io.github.kirillrublevsky.soulblade.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.kirillrublevsky.soulblade.model.BladeData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class SoulbladeStorage implements AutoCloseable {
    public record KillResult(BladeData blade, boolean counted, boolean duplicateEvent, String reason) {}
    private final HikariDataSource dataSource;
    private final ThreadPoolExecutor executor;
    private final String serverId;
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    public SoulbladeStorage(Plugin plugin) throws SQLException {
        FileConfiguration c = plugin.getConfig();
        boolean maria = c.getString("storage.type", "SQLITE").equalsIgnoreCase("MARIADB");
        HikariConfig hc = new HikariConfig();
        if (maria) {
            String base = "storage.mariadb.";
            hc.setJdbcUrl("jdbc:mariadb://" + c.getString(base + "host") + ':' + c.getInt(base + "port") + '/' + c.getString(base + "database"));
            hc.setUsername(c.getString(base + "username"));
            hc.setPassword(c.getString(base + "password"));
            hc.setMaximumPoolSize(Math.max(2, c.getInt(base + "pool-size", 4)));
        } else {
            Path db = plugin.getDataFolder().toPath().resolve(c.getString("storage.sqlite-file", "soulblade.db"));
            hc.setJdbcUrl("jdbc:sqlite:" + db.toAbsolutePath());
            hc.setMaximumPoolSize(1);
            hc.addDataSourceProperty("journal_mode", "WAL");
            hc.addDataSourceProperty("busy_timeout", "5000");
        }
        hc.setPoolName("OvenquillSoulblade");
        hc.setConnectionTimeout(5000);
        dataSource = new HikariDataSource(hc);
        int capacity = Math.max(128, c.getInt("storage.queue-capacity", 4096));
        executor = new ThreadPoolExecutor(maria ? 2 : 1, maria ? 2 : 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacity), r -> {
                Thread t = new Thread(r, "Soulblade-Storage"); t.setDaemon(true); return t;
            }, (r, e) -> { rejected.incrementAndGet(); throw new RejectedExecutionException("Soulblade storage queue full"); });
        serverId = c.getString("plugin.server-id", "backend-1");
        migrate(maria);
    }

    private void migrate(boolean maria) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS blade_instances (blade_uuid VARCHAR(36) PRIMARY KEY, created_at BIGINT NOT NULL, total_kills BIGINT NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', data_version INT NOT NULL, revision BIGINT NOT NULL DEFAULT 0, creator_uuid VARCHAR(36), creator_name VARCHAR(64), last_owner_uuid VARCHAR(36), last_owner_name VARCHAR(64), updated_at BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS blade_kill_events (event_id VARCHAR(36) PRIMARY KEY, blade_uuid VARCHAR(36) NOT NULL, victim_uuid VARCHAR(36), entity_type VARCHAR(64) NOT NULL, server_id VARCHAR(64) NOT NULL, occurred_at BIGINT NOT NULL, counted BOOLEAN NOT NULL, head_dropped BOOLEAN NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_kills_blade_time ON blade_kill_events(blade_uuid, occurred_at)");
            s.execute("CREATE TABLE IF NOT EXISTS player_kill_cooldowns (blade_uuid VARCHAR(36) NOT NULL, victim_uuid VARCHAR(36) NOT NULL, last_counted_at BIGINT NOT NULL, PRIMARY KEY(blade_uuid, victim_uuid))");
            s.execute("CREATE TABLE IF NOT EXISTS blade_owner_history (event_id VARCHAR(36) PRIMARY KEY, blade_uuid VARCHAR(36) NOT NULL, owner_uuid VARCHAR(36) NOT NULL, owner_name VARCHAR(64), occurred_at BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS admin_audit (audit_id VARCHAR(36) PRIMARY KEY, actor_uuid VARCHAR(36), action VARCHAR(64) NOT NULL, blade_uuid VARCHAR(36), previous_value VARCHAR(255), new_value VARCHAR(255), timestamp BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS duplicate_conflicts (conflict_id VARCHAR(36) PRIMARY KEY, blade_uuid VARCHAR(36) NOT NULL, first_holder VARCHAR(36), second_holder VARCHAR(36), detected_at BIGINT NOT NULL, resolved BOOLEAN NOT NULL DEFAULT FALSE)");
        }
    }

    public CompletableFuture<BladeData> create(UUID blade, UUID creator, String creatorName) {
        return submit(() -> {
            long now = System.currentTimeMillis();
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO blade_instances(blade_uuid,created_at,total_kills,status,data_version,revision,creator_uuid,creator_name,last_owner_uuid,last_owner_name,updated_at) VALUES(?,?,0,'ACTIVE',1,0,?,?,?,?,?)")) {
                p.setString(1, blade.toString()); p.setLong(2, now); p.setString(3, creator.toString()); p.setString(4, creatorName);
                p.setString(5, creator.toString()); p.setString(6, creatorName); p.setLong(7, now); p.executeUpdate();
            }
            return getSync(blade).orElseThrow();
        });
    }

    public CompletableFuture<Optional<BladeData>> get(UUID blade) { return submit(() -> getSync(blade)); }

    private Optional<BladeData> getSync(UUID blade) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement("SELECT * FROM blade_instances WHERE blade_uuid=?")) {
            p.setString(1, blade.toString());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(read(r));
            }
        }
    }

    public CompletableFuture<KillResult> countKill(UUID eventId, UUID blade, UUID victim, String entityType,
                                                    boolean player, long cooldownMillis, boolean headDropped) {
        return submit(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    if (eventExists(c, eventId)) { c.rollback(); return new KillResult(getSync(c, blade).orElseThrow(), false, true, "duplicate-event"); }
                    long now = System.currentTimeMillis();
                    boolean counted = true;
                    String reason = "counted";
                    if (player && victim != null && cooldownActive(c, blade, victim, now - cooldownMillis)) { counted = false; reason = "pvp-cooldown"; }
                    if (counted) {
                        try (PreparedStatement p = c.prepareStatement("UPDATE blade_instances SET total_kills=total_kills+1, revision=revision+1, updated_at=? WHERE blade_uuid=? AND status='ACTIVE'")) {
                            p.setLong(1, now); p.setString(2, blade.toString());
                            if (p.executeUpdate() != 1) throw new SQLException("Unknown or blocked blade " + blade);
                        }
                        if (player && victim != null) upsertCooldown(c, blade, victim, now);
                    }
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO blade_kill_events(event_id,blade_uuid,victim_uuid,entity_type,server_id,occurred_at,counted,head_dropped) VALUES(?,?,?,?,?,?,?,?)")) {
                        p.setString(1, eventId.toString()); p.setString(2, blade.toString()); p.setString(3, victim == null ? null : victim.toString());
                        p.setString(4, entityType); p.setString(5, serverId); p.setLong(6, now); p.setBoolean(7, counted); p.setBoolean(8, counted && headDropped); p.executeUpdate();
                    }
                    c.commit();
                    return new KillResult(getSync(c, blade).orElseThrow(), counted, false, reason);
                } catch (Exception e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
            }
        });
    }

    private Optional<BladeData> getSync(Connection c, UUID blade) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM blade_instances WHERE blade_uuid=?")) {
            p.setString(1, blade.toString());
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(read(r)) : Optional.empty(); }
        }
    }

    private boolean eventExists(Connection c, UUID id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM blade_kill_events WHERE event_id=?")) { p.setString(1,id.toString()); try(ResultSet r=p.executeQuery()){return r.next();} }
    }
    private boolean cooldownActive(Connection c, UUID blade, UUID victim, long since) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM player_kill_cooldowns WHERE blade_uuid=? AND victim_uuid=? AND last_counted_at>?")) {
            p.setString(1,blade.toString()); p.setString(2,victim.toString()); p.setLong(3,since); try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }
    private void upsertCooldown(Connection c, UUID blade, UUID victim, long now) throws SQLException {
        try (PreparedStatement d=c.prepareStatement("DELETE FROM player_kill_cooldowns WHERE blade_uuid=? AND victim_uuid=?")) { d.setString(1,blade.toString()); d.setString(2,victim.toString()); d.executeUpdate(); }
        try (PreparedStatement p=c.prepareStatement("INSERT INTO player_kill_cooldowns(blade_uuid,victim_uuid,last_counted_at) VALUES(?,?,?)")) { p.setString(1,blade.toString());p.setString(2,victim.toString());p.setLong(3,now);p.executeUpdate(); }
    }

    public CompletableFuture<BladeData> setKills(UUID blade, long kills) {
        return submit(() -> {
            try(Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement("UPDATE blade_instances SET total_kills=?,revision=revision+1,updated_at=? WHERE blade_uuid=?")) {
                p.setLong(1,Math.max(0,kills));p.setLong(2,System.currentTimeMillis());p.setString(3,blade.toString()); if(p.executeUpdate()!=1)throw new SQLException("Unknown blade");
            } return getSync(blade).orElseThrow();
        });
    }

    public CompletableFuture<Long> countBlades() { return submit(() -> { try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM blade_instances")){r.next();return r.getLong(1);} }); }

    private BladeData read(ResultSet r) throws SQLException {
        return new BladeData(UUID.fromString(r.getString("blade_uuid")), r.getLong("total_kills"), r.getLong("revision"),
            uuid(r.getString("creator_uuid")), r.getString("creator_name"), uuid(r.getString("last_owner_uuid")), r.getString("last_owner_name"),
            Instant.ofEpochMilli(r.getLong("created_at")), r.getString("status"));
    }
    private UUID uuid(String value) { return value == null ? null : UUID.fromString(value); }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try { executor.execute(() -> { try { future.complete(task.call()); } catch(Throwable t){future.completeExceptionally(t);} finally {completed.incrementAndGet();} }); }
        catch (RejectedExecutionException e) { future.completeExceptionally(e); }
        return future;
    }
    public int queueSize(){return executor.getQueue().size();}
    public long completedOperations(){return completed.get();}
    public long rejectedOperations(){return rejected.get();}
    public int activeOperations(){return executor.getActiveCount();}

    @Override public void close() {
        executor.shutdown();
        try { executor.awaitTermination(15, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        dataSource.close();
    }
}
