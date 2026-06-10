package so.alaz.provouchers.storage;

/** Supported storage backends. SQLite is the zero-config, file-based default. */
public enum Backend {
    SQLITE("org.sqlite.JDBC"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    MARIADB("org.mariadb.jdbc.Driver"),
    POSTGRES("org.postgresql.Driver");

    private final String driverClassName;

    Backend(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    /**
     * The JDBC driver class for this backend. Set explicitly on the pool so HikariCP loads the
     * driver from the plugin classloader: the drivers are fetched by Paper's library loader onto
     * that isolated loader, where {@link java.sql.DriverManager}'s auto-registration cannot see
     * them, so leaving HikariCP to find a driver by URL alone fails with "No suitable driver".
     */
    public String driverClassName() {
        return driverClassName;
    }
}
