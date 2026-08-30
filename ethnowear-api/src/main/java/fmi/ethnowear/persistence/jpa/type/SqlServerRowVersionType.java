package fmi.ethnowear.persistence.jpa.type;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserVersionType;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

/**
 * Maps a SQL Server {@code ROWVERSION} column as a Hibernate-managed optimistic-lock version.
 *
 * <p>SQL Server exposes {@code ROWVERSION} through JDBC as the fixed-width {@link Types#BINARY}
 * type, while Hibernate normally maps a {@code byte[]} version to {@link Types#VARBINARY}.
 * This type preserves Hibernate's version comparison behavior while keeping schema validation
 * aligned with the database-managed eight-byte value.</p>
 *
 * <p>The application never generates or increments the value. SQL Server assigns a new value
 * whenever the owning row is inserted or updated.</p>
 */
public class SqlServerRowVersionType implements UserVersionType<byte[]> {

    /**
     * Uses the JDBC type reported by SQL Server for {@code ROWVERSION} columns.
     */
    @Override
    public int getSqlType() {
        return Types.BINARY;
    }

    @Override
    public Class<byte[]> returnedClass() {
        return byte[].class;
    }

    @Override
    public boolean equals(byte[] left, byte[] right) {
        return Arrays.equals(left, right);
    }

    @Override
    public int hashCode(byte[] value) {
        return Arrays.hashCode(value);
    }

    @Override
    public byte[] nullSafeGet(
            @NonNull ResultSet resultSet,
            int position,
            SharedSessionContractImplementor session,
            Object owner
    ) throws SQLException {
        return resultSet.getBytes(position);
    }

    @Override
    public void nullSafeSet(
            PreparedStatement statement,
            byte[] value,
            int index,
            SharedSessionContractImplementor session
    ) throws SQLException {
        if (value == null)
            statement.setNull(index, Types.BINARY);
        else
            statement.setBytes(index, value);
    }

    @Override
    public byte[] deepCopy(byte[] value) {
        return value == null ? null : value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(byte[] value) {
        return deepCopy(value);
    }

    @Override
    public byte[] assemble(Serializable cached, Object owner) {
        return deepCopy((byte[]) cached);
    }

    @Override
    public int compare(byte[] left, byte[] right) {
        if (left == right)
            return 0;
        if (left == null)
            return -1;
        if (right == null)
            return 1;

        return Arrays.compareUnsigned(left, right);
    }

    /**
     * Returns no initial value because SQL Server generates it during insertion.
     */
    @Override
    public byte[] seed(SharedSessionContractImplementor session) {
        return null;
    }

    /**
     * Leaves version generation to SQL Server instead of incrementing it in Java.
     */
    @Override
    public byte[] next(byte[] current, SharedSessionContractImplementor session) {
        return current;
    }
}
