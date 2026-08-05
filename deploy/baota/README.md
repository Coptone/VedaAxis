# BaoTa test deployment

This deployment is deliberately isolated from the existing `coptone.link`
application:

- Web and release files: `/www/wwwroot/VedaAxis`
- API: BaoTa Java project `vedaaxis-api`, bound to `127.0.0.1:18085`
- PostgreSQL: Docker Compose project `vedaaxis`, bound to
  `127.0.0.1:55432` with named volume `vedaaxis-postgres`
- Public paths: `/VedaAxis/`, `/VedaAxis/api/`, and
  `/VedaAxis/release/`

Copy `.env.example` to `.env` on the server and generate a unique database
password. Configure the Java project with the values in
`vedaaxis-java.env.example`, using the same database password and a separate
random JWT secret. Do not commit either secret.

Add the marker-scoped block from `nginx-vedaaxis.conf` to the existing
`coptone.link` server block. Back up the generated vhost first, run `nginx -t`,
and only then reload Nginx. The rules do not change the site's root or catch
requests outside `/VedaAxis/`. The static location deliberately uses `^~` so
the parent WordPress site's global JavaScript/CSS regular expressions cannot
intercept VedaAxis assets.

The deployed API is a systemd service using Java 21 and binds only to
`127.0.0.1:18085`. PostgreSQL uses the pinned image digest in `compose.yaml`,
binds only to `127.0.0.1:55432`, and stores data in the dedicated
`vedaaxis-postgres` volume. Secrets are generated on the server and kept in
root-readable environment files outside the web root.

The plugin repository endpoint is:

`https://coptone.link/VedaAxis/pluginmaster.json`

The stable latest-package URL used by that manifest is:

`https://coptone.link/VedaAxis/release/latest/VedaAxis.zip`

`autoindex off`, exact file lookup, HTTPS, and the release SHA-256 check are
required before advertising the endpoint.
