package com.travbook.customer.adapter.out.persistence;
import com.travbook.customer.application.port.out.CustomerRepository;
import com.travbook.customer.domain.model.Customer;
import com.travbook.shared.domain.CustomerIdentity;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
public final class JdbcCustomers implements CustomerRepository {
    private final JdbcClient jdbc;
    public JdbcCustomers(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Customer findOrCreate(CustomerIdentity identity,Instant now) {
        jdbc.sql("insert into customer_account(id,issuer,subject,created_at) values(:id,:issuer,:subject,:now) on conflict(issuer,subject) do nothing")
            .param("id",UUID.randomUUID()).param("issuer",identity.issuer()).param("subject",identity.subject()).param("now",Timestamp.from(now)).update();
        // Separate READ COMMITTED statement observes a competing insert after uniqueness arbitration.
        return jdbc.sql("select id,created_at from customer_account where issuer=:issuer and subject=:subject")
            .param("issuer",identity.issuer()).param("subject",identity.subject())
            .query((rs,n)->new Customer(rs.getObject("id",UUID.class),rs.getTimestamp("created_at").toInstant())).single();
    }
}

