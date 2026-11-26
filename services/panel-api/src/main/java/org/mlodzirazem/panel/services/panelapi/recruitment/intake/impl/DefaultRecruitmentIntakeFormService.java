/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mlodzirazem.panel.services.panelapi.recruitment.intake.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentIntake;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentIntakeRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.IntakeFormException;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.IntakeProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.IntakeSubmission;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.RecruitmentIntakeFormService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
class DefaultRecruitmentIntakeFormService implements RecruitmentIntakeFormService {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9][0-9 -]{5,30}");
    private static final int NAME_MAX = 100;
    private static final int EMAIL_MAX = 254;
    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    /** Any constant works; it only has to be the same for every submission of every instance. */
    private static final long SUBMISSION_LOCK = 0x50414E454C494E54L;

    private final IntakeProperties properties;
    private final IntakeFormTokens tokens;
    private final ClientRateLimiter clientLimiter;
    private final IntakeJournal journal;
    private final TRecruitmentIntakeRepository intakes;
    private final TBodyRepository bodies;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Form open() {
        if (!properties.isEnabled()) {
            return new Form(false, "", CONSENT_VERSION, List.of());
        }

        List<District> districts = bodies.findAllByKind(BodyKind.DISTRICT).stream()
            .map(body -> new District(body.getId(), body.getName()))
            .sorted(Comparator.comparing(District::name, java.text.Collator.getInstance(
                java.util.Locale.forLanguageTag("pl"))))
            .toList();

        return new Form(true, tokens.issue(), CONSENT_VERSION, districts);
    }

    @Override
    @Transactional
    public void submit(IntakeSubmission submission) {
        if (!properties.isEnabled()) {
            throw new IntakeFormException(IntakeFormException.Kind.DISABLED, "INTAKE_DISABLED",
                "The form is not accepting submissions", List.of());
        }

        // a caught automated sender is told the same as everybody else
        if (!isBlank(submission.website())) {
            return;
        }

        Duration age = tokens.age(submission.formToken())
            .filter(value -> value.compareTo(Duration.ofHours(properties.getFormValidHours())) <= 0)
            .orElseThrow(() -> invalid("FORM_EXPIRED", "formToken"));

        if (age.compareTo(Duration.ofSeconds(properties.getMinFillSeconds())) < 0) {
            return;
        }

        TRecruitmentIntake intake = validated(submission);

        // one submission at a time: the limits and the "one waiting submission per e-mail" rule are then exact
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, SUBMISSION_LOCK);

        Instant now = clock.instant();
        requireCapacity(now);

        String client = submission.clientAddress();

        if (!isBlank(client) && !clientLimiter.tryAcquire(client.strip(), properties.getMaxPerClientPerHour())) {
            throw busy();
        }

        // from here on a repeat and a new submission differ only in what is stored, never in what is answered
        Long intakeId = null;

        if (!intakes.existsPendingByEmail(intake.getEmail())) {
            ZonedDateTime when = ZonedDateTime.ofInstant(now, ZoneOffset.UTC);
            intake.setCreatedAt(when);
            intake.setConsentedAt(when);
            intakeId = intakes.saveAndFlush(intake).getId();
            journal.record("RECEIVED", intakeId, "intake-received", storedFields(intake), "PENDING", null);
        }

        jdbc.update("INSERT INTO recruitment_intake_slots (created_at, intake_id) VALUES (?, ?)",
            Timestamp.from(now), intakeId);
    }

    private void requireCapacity(Instant now) {
        if (slotsSince(now.minus(Duration.ofHours(1)), false) >= properties.getMaxPerHour()
            || slotsSince(now.minus(Duration.ofDays(1)), false) >= properties.getMaxPerDay()
            || slotsSince(now.minus(Duration.ofDays(properties.getPendingRetentionDays())), true)
            >= properties.getMaxPending()) {
            throw busy();
        }
    }

    private long slotsSince(Instant since, boolean unreleasedOnly) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_intake_slots WHERE created_at > ?"
            + (unreleasedOnly ? " AND released_at IS NULL" : ""), Long.class, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    private TRecruitmentIntake validated(IntakeSubmission submission) {
        if (!CONSENT_VERSION.equals(submission.consentVersion())) {
            throw invalid("FORM_EXPIRED", "formToken");
        }

        if (!submission.consent()) {
            throw invalid("CONSENT_REQUIRED", "consent");
        }

        TRecruitmentIntake intake = new TRecruitmentIntake();
        intake.setConsentVersion(CONSENT_VERSION);
        intake.setFirstName(required(submission.firstName(), "firstName", NAME_MAX));
        intake.setLastName(required(submission.lastName(), "lastName", NAME_MAX));

        String email = required(submission.email(), "email", EMAIL_MAX);

        if (!EMAIL.matcher(email).matches()) {
            throw invalid("INVALID_VALUE", "email");
        }

        intake.setEmail(email);

        String phone = optional(submission.phone(), "phone", NAME_MAX);

        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw invalid("INVALID_VALUE", "phone");
        }

        intake.setPhone(phone);
        intake.setBirthDate(admissibleBirthDate(required(submission.birthDate(), "birthDate", NAME_MAX)));

        Long districtId = submission.districtBodyId();

        if (districtId == null) {
            intake.setLocality(required(submission.locality(), "locality", NAME_MAX));
        } else {
            intake.setDistrict(bodies.findById(districtId).filter(TBody::isDistrict)
                .orElseThrow(() -> invalid("INVALID_VALUE", "districtBodyId")));
        }

        return intake;
    }

    /** Depends on the submitted date alone, so refusing it reveals nothing. Nothing is stored when refused. */
    private LocalDate admissibleBirthDate(String text) {
        LocalDate birthDate;

        try {
            birthDate = LocalDate.parse(text);
        } catch (DateTimeParseException malformed) {
            throw invalid("INVALID_VALUE", "birthDate");
        }

        LocalDate today = LocalDate.now(clock.withZone(WARSAW));

        if (birthDate.isBefore(EARLIEST_BIRTH_DATE) || birthDate.isAfter(today)) {
            throw invalid("INVALID_VALUE", "birthDate");
        }

        try {
            MemberRules.validateAdmissionAge(birthDate, MembershipType.ORDINARY, today);
        } catch (RuleViolationException outside) {
            throw invalid("ADMISSION_AGE", "birthDate");
        }

        return birthDate;
    }

    private static String required(@Nullable String value, String field, int max) {
        String normalized = optional(value, field, max);

        if (normalized == null) {
            throw invalid("FIELD_REQUIRED", field);
        }

        return normalized;
    }

    private static @Nullable String optional(@Nullable String value, String field, int max) {
        if (isBlank(value)) {
            return null;
        }

        String trimmed = value.strip();

        if (trimmed.length() > max || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw invalid("INVALID_VALUE", field);
        }

        return trimmed;
    }

    private static List<String> storedFields(TRecruitmentIntake intake) {
        List<String> fields = new ArrayList<>(List.of("firstName", "lastName", "email", "birthDate", "consent"));

        if (intake.getPhone() != null) {
            fields.add("phone");
        }

        fields.add(intake.getDistrict() == null ? "locality" : "districtBodyId");
        return fields;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static IntakeFormException invalid(String code, String field) {
        return new IntakeFormException(IntakeFormException.Kind.INVALID, code,
            "The form cannot be accepted", List.of(field));
    }

    private static IntakeFormException busy() {
        return new IntakeFormException(IntakeFormException.Kind.BUSY, "INTAKE_BUSY",
            "Too many submissions right now, try again later", List.of());
    }
}
