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
     * The JDBC driver class for this backend. The pool names it explicitly because Paper's library
     * loader puts the drivers on the plugin classloader, where {@link java.sql.DriverManager} cannot
     * find them by URL.
     */
    public String driverClassName() {
        return driverClassName;
    }
}
