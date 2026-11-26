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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentIntakeDiscardReason;

/** Back-office actions on a waiting submission. Permissions are checked by the controller. */
public interface RecruitmentIntakeQueueService {
    /** Moves a waiting submission to another district. */
    void assignDistrict(long intakeId, long districtBodyId, long actorMemberId);

    /** Opens a recruitment case (source WEBSITE) and erases the personal data of the submission. */
    long accept(long intakeId, long actorMemberId);

    /** Discards a waiting submission; spam and withdrawals are erased at once. */
    void discard(long intakeId, RecruitmentIntakeDiscardReason reason, long actorMemberId);

    /** Erases a discarded submission before its retention period ends, e.g. when consent is withdrawn later. */
    void erase(long intakeId, long actorMemberId);

    /** The retention run: expires unhandled submissions and erases discarded ones that are due. */
    void applyRetention();
}
