/*
 * Copyright 2025 Matthias Bläsing.
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

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.UtilUI;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/**
 * Client for Bitwarden Systems
 */
public class BitwardenMain {

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle");
    private static final System.Logger LOG = System.getLogger(BitwardenMain.class.getName());

    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger(BitwardenClient.class.getName());
        log.setLevel(Level.FINEST);

        for (Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }

        BitwardenClient bwClient = new BitwardenClient();
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            if (SystemInfo.isLinux) {
                // enable custom window decorations
                JFrame.setDefaultLookAndFeelDecorated(true);
                JDialog.setDefaultLookAndFeelDecorated(true);
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Class<?> xtoolkit = toolkit.getClass();
                //#183739 - provide proper app name on Linux
                if (xtoolkit.getName().equals("sun.awt.X11.XToolkit")) { //NOI18N
                    try {
                        final Field awtAppClassName = xtoolkit.getDeclaredField("awtAppClassName"); //NOI18N
                        awtAppClassName.setAccessible(true);
                        awtAppClassName.set(null, "BitwardenAgent"); //NOI18N
                    } catch (Exception x) {
                        LOG.log(System.Logger.Level.WARNING, "", x);
                    }
                }
            }
            JFrame frame = new JFrame("BitwardenAgent");
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
            try(InputStream is = BitwardenMain.class.getResourceAsStream("/icon.png")) {
                BufferedImage bi = ImageIO.read(is);
                frame.setIconImage(bi);
            } catch (IOException ex) {
                LOG.log(System.Logger.Level.WARNING, "Failed to load application icon", ex);
            }
            JMenuBar menuBar = new JMenuBar();
            JMenuItem exit = new JMenuItem(RESOURCE_BUNDLE.getString("menuItem.exit"));
            exit.addActionListener(ae -> System.exit(0));
            JMenuItem refresh = new JMenuItem(RESOURCE_BUNDLE.getString("menuItem.refresh"));
            bwClient.addStateObserver((oldState, newState) -> {
                SwingUtilities.invokeLater(() -> {
                    refresh.setEnabled(newState == BitwardenClient.State.Syncable);
                });
            });
            refresh.addActionListener(ae -> {
                UtilUI.runOffTheEdt(
                        () -> bwClient.sync(),
                        () -> {
                        },
                        (exception) -> {
                            LOG.log(System.Logger.Level.WARNING, "Failed to set master password", exception);
                        }
                );
            });
            JMenu fileMenu = new JMenu(RESOURCE_BUNDLE.getString("menuItem.file"));
            menuBar.add(fileMenu);
            fileMenu.add(refresh);
            fileMenu.add(exit);
            frame.setJMenuBar(menuBar);
            frame.setLayout(new BorderLayout());
            frame.add(new BitwardenMainPanel(bwClient, menuBar), BorderLayout.CENTER);
            frame.setSize(800, 600);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });

    }
}
