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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.body;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "body_template_incompatibilities")
public class TBodyTemplateIncompatibility {
    @EmbeddedId
    private Key id;

    @Column(name = "single_member_exempt_template_id")
    private @Nullable Long singleMemberExemptTemplateId;

    public Long getBodyTemplateId() {
        return id.bodyTemplateId;
    }

    public Long getOtherBodyTemplateId() {
        return id.otherBodyTemplateId;
    }

    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "body_template_id")
        private Long bodyTemplateId;

        @Column(name = "other_body_template_id")
        private Long otherBodyTemplateId;
    }
}
