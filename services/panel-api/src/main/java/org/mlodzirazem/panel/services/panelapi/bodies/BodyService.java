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

package org.mlodzirazem.panel.services.panelapi.bodies;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplate;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;

import java.util.List;

public interface BodyService {
    TBody get(long bodyId);

    List<TBody> all();

    List<TBody> districts();

    /**
     * A body of a LOCAL template with a parent template needs a parent body of that template; other bodies must
     * not have a parent. Audited.
     */
    TBody create(NewBody newBody);

    TBody update(long bodyId, UpdateBody update);

    List<TBodyTemplate> templates();

    List<TBodyTemplateMemberType> memberTypes(long templateId);
}
