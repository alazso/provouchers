package so.alaz.provouchers.storage;

/** Supported storage backends. SQLite is the zero-config, file-based default. */
public enum Backend {
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRES,
}
