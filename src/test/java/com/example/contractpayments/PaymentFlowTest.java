package com.example.contractpayments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE outbox_events, payments, jobs, contracts, profiles CASCADE");
    }

    private MvcResult send(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }
    private String id(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
    private String profile(String role, int balance) throws Exception {
        return id(send("/profiles", "{\"name\":\"" + role + "\",\"role\":\"" + role + "\",\"balanceCents\":" + balance + "}"));
    }
    private String contract(String client, String contractor) throws Exception {
        return id(send("/contracts", "{\"clientId\":\"" + client + "\",\"contractorId\":\"" + contractor + "\",\"title\":\"Design work\"}"));
    }
    private String job(String contract, int amount) throws Exception {
        return id(send("/jobs", "{\"contractId\":\"" + contract + "\",\"title\":\"Logo\",\"amountCents\":" + amount + "}"));
    }
    private MvcResult pay(String job, String key) throws Exception {
        return mvc.perform(post("/jobs/" + job + "/payments").header("Idempotency-Key", key)).andReturn();
    }
    private int balance(String profile) {
        return jdbc.queryForObject("SELECT balance_cents FROM profiles WHERE id = ?::uuid", Integer.class, profile);
    }

    @Test void clientPaysContractorForAJobExactlyOnce() throws Exception {
        String client = profile("CLIENT", 1000), contractor = profile("CONTRACTOR", 200);
        String contract = contract(client, contractor), job = job(contract, 300);
        MvcResult first = pay(job, "pay-job-100");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getContentAsString()).contains("COMPLETED");
        assertThat(balance(client)).isEqualTo(700);
        assertThat(balance(contractor)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT status FROM jobs WHERE id = ?::uuid", String.class, job)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type = 'JobPaid'", Integer.class)).isEqualTo(1);
        assertThat(pay(job, "pay-job-100").getResponse().getStatus()).isEqualTo(200);
        assertThat(pay(job, "another-key").getResponse().getStatus()).isEqualTo(409);
        assertThat(balance(client)).isEqualTo(700);
    }

    @Test void twentyConcurrentJobsCannotOverdrawTheClient() throws Exception {
        String client = profile("CLIENT", 1000), contractor = profile("CONTRACTOR", 0);
        String contract = contract(client, contractor);
        List<String> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) jobs.add(job(contract, 100));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(20)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (String j : jobs) results.add(pool.submit(() -> { start.await(); return pay(j, UUID.randomUUID().toString()).getResponse().getStatus(); }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) statuses.add(result.get());
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(10);
            assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(10);
        }
        assertThat(balance(client)).isZero();
        assertThat(balance(contractor)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(10);
    }

    @Test void duplicateConcurrentJobPayRequestsReturnOnePayment() throws Exception {
        String client = profile("CLIENT", 100), contractor = profile("CONTRACTOR", 0);
        String job = job(contract(client, contractor), 100);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) results.add(pool.submit(() -> { start.await(); return pay(job, "same-key").getResponse().getStatus(); }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) statuses.add(result.get());
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(11);
        }
        assertThat(balance(client)).isZero();
        assertThat(balance(contractor)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
    }

    @Test void contractRequiresClientAndContractorRoles() throws Exception {
        String a = profile("CLIENT", 100), b = profile("CLIENT", 0);
        assertThat(send("/contracts", "{\"clientId\":\"" + a + "\",\"contractorId\":\"" + b + "\",\"title\":\"X\"}").getResponse().getStatus()).isEqualTo(422);
    }

    @Test void invalidAmountsAndMissingIdempotencyKeyAreRejected() throws Exception {
        String a = profile("CLIENT", 100), b = profile("CONTRACTOR", 0), contract = contract(a, b);
        assertThat(send("/jobs", "{\"contractId\":\"" + contract + "\",\"title\":\"X\",\"amountCents\":-1}").getResponse().getStatus()).isEqualTo(400);
        String job = job(contract, 10);
        assertThat(mvc.perform(post("/jobs/" + job + "/payments")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(balance(a)).isEqualTo(100);
    }

    @Test void profileContractAndJobCanBeReadBack() throws Exception {
        String a = profile("CLIENT", 100), b = profile("CONTRACTOR", 0), c = contract(a, b), j = job(c, 10);
        assertThat(mvc.perform(get("/profiles/" + a)).andReturn().getResponse().getContentAsString()).contains("CLIENT");
        assertThat(mvc.perform(get("/contracts/" + c)).andReturn().getResponse().getContentAsString()).contains(a, b);
        assertThat(mvc.perform(get("/jobs/" + j)).andReturn().getResponse().getContentAsString()).contains("OPEN");
    }

    @Test void sameKeyOnDifferentConcurrentJobsCannotCreateTwoPayments() throws Exception {
        String a = profile("CLIENT", 1000), b = profile("CONTRACTOR", 0);
        String c = contract(a, b), firstJob = job(c, 100), secondJob = job(c, 100);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> one = pool.submit(() -> { start.await(); return pay(firstJob, "shared-key").getResponse().getStatus(); });
            Future<Integer> two = pool.submit(() -> { start.await(); return pay(secondJob, "shared-key").getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(balance(a)).isEqualTo(900);
        assertThat(balance(b)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs WHERE status = 'PAID'", Integer.class)).isEqualTo(1);
    }

    @Test void failedFundsCheckLeavesJobOpenAndKeyReusable() throws Exception {
        String a = profile("CLIENT", 10), b = profile("CONTRACTOR", 0), j = job(contract(a, b), 100);
        assertThat(pay(j, "retry-key").getResponse().getStatus()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
        jdbc.update("UPDATE profiles SET balance_cents = 100 WHERE id = ?::uuid", a);
        assertThat(pay(j, "retry-key").getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(a)).isZero();
        assertThat(balance(b)).isEqualTo(100);
    }

    @Test void databaseConstraintsRejectNegativeBalancesAndDuplicateJobPayments() throws Exception {
        String a = profile("CLIENT", 100), b = profile("CONTRACTOR", 0), j = job(contract(a, b), 10);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbc.update("UPDATE profiles SET balance_cents = -1 WHERE id = ?::uuid", a)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(pay(j, "first-key").getResponse().getStatus()).isEqualTo(201);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbc.update("INSERT INTO payments (id, job_id, amount_cents, idempotency_key, status) VALUES (?::uuid, ?::uuid, 10, 'second-key', 'COMPLETED')", UUID.randomUUID().toString(), j))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
    }

    @Test void failureAfterPaymentWritesRollsBackBalancesJobAndPayment() throws Exception {
        String a = profile("CLIENT", 100), b = profile("CONTRACTOR", 0), j = job(contract(a, b), 50);
        jdbc.execute("CREATE FUNCTION reject_paid_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status = 'PAID' THEN RAISE EXCEPTION 'intentional test failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_paid_job BEFORE UPDATE OF status ON jobs FOR EACH ROW EXECUTE FUNCTION reject_paid_job()");
        try {
            assertThat(pay(j, "try-once").getResponse().getStatus()).isEqualTo(500);
            assertThat(balance(a)).isEqualTo(100);
            assertThat(balance(b)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM jobs WHERE id = ?::uuid", String.class, j)).isEqualTo("OPEN");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_paid_job ON jobs");
            jdbc.execute("DROP FUNCTION reject_paid_job()");
        }
        assertThat(pay(j, "try-once").getResponse().getStatus()).isEqualTo(201);
    }
}
