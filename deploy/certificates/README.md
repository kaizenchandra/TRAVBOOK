# Public RDS CA bundle

Downloaded over verified HTTPS on 2026-10-31 from the [official AWS RDS global trust store](https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem). This file contains public CA certificates, not private keys or credentials.

SHA-256: `90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b`.

The API image checks this hash and installs the bundle at `/app/certs/rds-global-bundle.pem`. Terraform configures PostgreSQL `sslmode=verify-full`, requiring certificate and hostname verification. Local Compose remains on its existing local database connection. No RDS connection has been tested yet. Refresh the bundle through a reviewed change, updating both this evidence and the Dockerfile hash; never disable TLS verification to bypass rotation failures. [AWS guidance](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).
