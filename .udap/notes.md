# owaspcheck — working notes

## What this is
Internal Developer Portal for an enterprise. Spring Boot 3.3 / Java 21 REST API
with JWT auth, CRUD for Projects/Teams/Environments/Deployments, PostgreSQL
backend, containerized, deployed to AWS EC2 behind nginx.

Project meta: aws / us-east-1 / ec2 / github / repo `owaspcheck` / branch main.

## Key decisions and WHY

- **No marketplace blueprint.** `springboot-ec2` was the only match but ships
  Java 17, no DB, no Docker, no Puppet, and builds the jar on the box under
  systemd. Every one of those would have been replaced. Built custom instead.

- **Scaffold POM was discarded.** Spring Initializr returned Spring Boot 4.1.1
  with non-existent artifact IDs (`spring-boot-starter-webmvc`,
  `spring-boot-starter-data-jpa-test`, `spring-boot-starter-actuator-test`).
  Those would have failed the first `mvn compile`. Replaced the whole POM with
  Spring Boot 3.3.5 + real artifact IDs. Package moved
  `com.example.owaspcheck` → `com.enterprise.idp`.

- **Puppet + Ansible split (user asked for both).** Puppet is NOT a
  platform-native configure mechanism — Ansible is the default for VM targets.
  Implemented Puppet as MASTERLESS `puppet apply` (no Puppet server to run,
  correct for a single instance) in a `custom` stage between provision and
  configure. Puppet owns the server baseline (Java, Docker, users, hardening);
  Ansible owns app delivery (nginx, container, env, start).

- **RDS PostgreSQL, not a container DB.** `/actuator/health` includes the JPA
  datasource check, so a 200 also proves DB connectivity. Private subnets, SG
  ingress only from the app SG.

- **configure stage reads terraform output itself.** Does NOT thread the RDS
  endpoint through `needs.provision.outputs` — GitHub drops job outputs
  containing secret substrings and PROJECT_NAME is a secret, so a derived
  endpoint would arrive empty. Re-runs `terraform init` with identical backend
  flags and reads `terraform output -raw db_address`. (Platform pitfall #3.)

- **ansible.builtin ONLY.** ansible-core ships no community.docker. Container
  lifecycle is `ansible.builtin.command` (docker login/pull/prune) plus a
  systemd unit — no collection install needed.

- **No host firewall in Puppet hardening.** Deliberate. SG already limits to
  22/80/443; enabling ufw during bootstrap before Ansible configures nginx risks
  locking CI out. Documented in the hardening module and deployment summary.

- **NVD key via POM `${env.NVD_API_KEY}`, not a CLI flag.** The literal
  `-DnvdApiKey=` tripped the secret scanner. Plugin now reads the env var that
  CI populates from the repo secret.

## Gotchas hit while generating

- `baseline` and `users` both declared `/opt/idp` and `/var/log/idp` →
  duplicate resource declaration = Puppet compile error. Removed from
  `baseline`; `users` owns them.
- `site.pp` originally passed `stage =>` to classes without declaring stages.
  Replaced with `include` + explicit `->` chaining.
- `java` module had a pointless `verify_java_version` exec. Removed.
- `ansible.builtin.command` `stdin` is a top-level `args:` key, not a sub-key
  of `cmd:`. Fixed GHCR login to use `argv:` + `args: stdin:`.
- First `write_pipeline` refused: stray non-ASCII char (`ȷ`) before a `run:`.
- Surefire/Failsafe split: `*Test` vs `*IT`. Integration stage uses
  `mvn verify -Dsurefire.skip=true` (NOT a made-up `-Pintegration` profile).
- Every ansible `command` task needs `changed_when` or the idempotency
  validator warns. All now have explicit `changed_when` + `failed_when`.

## test_project rehearsal (2026-08-26)
PASSED: mvn clean compile; **27/27 unit + API tests green**, jar repackaged.
FAILED: `CatalogCrudIT` — `NoSuchFileException (/var/run/docker.sock)`.
=> SANDBOX GAP, not a defect. UDAP's sandbox has no Docker daemon;
   Testcontainers requires one. GitHub `ubuntu-latest` DOES ship Docker, so this
   passes in CI. Per platform rule 9 / rule 4, did NOT delete the test or swap
   it for H2 — it is the only check proving JPA entities and Flyway migrations
   agree. Expect this stage GREEN in real CI.

## Status
- [x] Meta approved, architecture + pipeline written, design approved
- [x] Plan approved
- [x] App code, tests, Docker, Terraform, Puppet, Ansible, docs generated
- [x] validate_project PASS (93 files)
- [x] test_project run (unit green; IT blocked by sandbox Docker gap)
- [ ] push, secrets, deploy, verify /actuator/health = 200

## Secrets to set AFTER create_repo_and_push (repo must exist first)
- `NVD_API_KEY` — supplied by the user (pasted in chat; TOLD THEM TO ROTATE IT)
- `DB_PASSWORD` — generate alphanumeric >=20
- `JWT_SECRET` — generate alphanumeric >=32 (app refuses to start below 32)
Never write values here.

## Known limitations shipped (documented, deliberate)
HTTP only (no domain → no TLS); single instance (no ALB/ASG); single-AZ RDS
with 7-day backups; SSH 0.0.0.0/0 (GitHub runner IPs are broad; key-only auth,
no root login); `skip_final_snapshot = true` on RDS.
