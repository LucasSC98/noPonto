package me.lucascustodio.noponto.domain;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.TimeUtcAsJdbcTimeJdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetTime;

/**
 * Grava e lê o {@link OffsetTime} com o relógio e o offset originais.
 * O tipo UTC do Hibernate transforma o valor em {@code java.sql.Time} no fuso da conexão,
 * e 08:00-03:00 acaba persistido como 11:00+00.
 */
public class TipoHorarioComFuso extends TimeUtcAsJdbcTimeJdbcType {

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new BasicBinder<>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
                    throws SQLException {
                st.setObject(index, horario(value, options));
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                st.setObject(name, horario(value, options));
            }

            @Override
            protected void doBindNull(PreparedStatement st, int index, WrapperOptions options)
                    throws SQLException {
                st.setNull(index, Types.OTHER);
            }

            @Override
            protected void doBindNull(CallableStatement st, String name, WrapperOptions options)
                    throws SQLException {
                st.setNull(name, Types.OTHER);
            }

            private OffsetTime horario(X value, WrapperOptions options) {
                return javaType.unwrap(value, OffsetTime.class, options);
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new BasicExtractor<>(javaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int index, WrapperOptions options) throws SQLException {
                return javaType.wrap(rs.getObject(index, OffsetTime.class), options);
            }

            @Override
            protected X doExtract(CallableStatement st, int index, WrapperOptions options) throws SQLException {
                return javaType.wrap(st.getObject(index, OffsetTime.class), options);
            }

            @Override
            protected X doExtract(CallableStatement st, String name, WrapperOptions options) throws SQLException {
                return javaType.wrap(st.getObject(name, OffsetTime.class), options);
            }
        };
    }
}
