/*
 * Copyright 2026 matthias.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.doppelhelix.app.bitwardenagent;

import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ResourceBundle;
import javax.swing.AbstractAction;

public class LogoutAction extends AbstractAction {

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle");

    private final BitwardenClient client;

    public LogoutAction(Component parentComponent, BitwardenClient client) {
        super(RESOURCE_BUNDLE.getString("menuItem.logout"));
        this.client = client;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        client.clear();
    }

}
