package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProvisioningFinished;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningOperation;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.SubjectKind;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3: the event is stored as JSON by Spring Modulith and read back after a restart; the round trip must give the same
 * event for both kinds of subject, without any type information in the payload.
 */
class AccountProvisioningFinishedSerializationTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void aMemberEventSurvivesAJsonRoundTrip() {
        AccountProvisioningFinished event = new AccountProvisioningFinished(11, ProvisioningOperation.CREATE_MEMBER_ACCOUNT,
            SubjectKind.MEMBER, 7, true, null, 3L, null, null);

        String text = json.writeValueAsString(event);
        AccountProvisioningFinished back = json.readValue(text, AccountProvisioningFinished.class);

        assertThat(back).isEqualTo(event);
        assertThat(back.subject()).isEqualTo(new AccountSubject.Member(7));
        assertThat(text).doesNotContain("@");
    }

    @Test
    void aCandidateFailureSurvivesAJsonRoundTrip() {
        AccountProvisioningFinished event = new AccountProvisioningFinished(12, ProvisioningOperation.DELETE_ACCOUNT,
            SubjectKind.CANDIDATE, 9, false, ProvisioningErrorCode.PRECONDITION_FAILED, null, null, 5L);

        AccountProvisioningFinished back = json.readValue(json.writeValueAsString(event), AccountProvisioningFinished.class);

        assertThat(back).isEqualTo(event);
        assertThat(back.subject()).isEqualTo(new AccountSubject.Candidate(9));
    }
}
