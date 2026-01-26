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
import eu.doppelhelix.app.bitwardenagent.server.UnixDomainSocketServer;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import static eu.doppelhelix.app.bitwardenagent.Configuration.PROP_ALLOW_ALL_ACCESS;
import static eu.doppelhelix.app.bitwardenagent.Configuration.PROP_START_UNIX_DOMAIN_SOCKET_SERVER;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Client for Bitwarden Systems
 */
public class BitwardenMain {

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("eu/doppelhelix/app/bitwardenagent/Bundle");
    private static final System.Logger LOG = System.getLogger(BitwardenMain.class.getName());

    private static Logger BWCLogger = Logger.getLogger(BitwardenClient.class.getName());

    private static ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(0);

    public static void main(String[] args) throws Exception {
        for(Handler h: Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }

        BWCLogger.setLevel(Level.FINEST);

        BitwardenClient bwClient = new BitwardenClient();

        AtomicReference<UnixDomainSocketServer> udss = new AtomicReference<>();
        AtomicReference<JCheckBoxMenuItem> enableServerReference = new AtomicReference<>();

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
                        LOG.log(WARNING, "", x);
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
                LOG.log(WARNING, "Failed to load application icon", ex);
            }

            LoginAction loginAction = new LoginAction(frame, bwClient);
            LogoutAction logoutAction = new LogoutAction(frame, bwClient);

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
                runSync(bwClient);
            });
            JCheckBoxMenuItem automaticSync = new JCheckBoxMenuItem(RESOURCE_BUNDLE.getString("menuItem.automaticSync"));
            automaticSync.addActionListener(ae -> {
                Configuration conf = Configuration.getConfiguration();
                conf.setAutomaticSync(conf.isAutomaticSync());
            });
            Configuration.getConfiguration().addObserver((name, value) -> {
                if (PROP_ALLOW_ALL_ACCESS.equals(name)) {
                    automaticSync.setState((boolean) value);
                }
            });
            automaticSync.setState(Configuration.getConfiguration().isAutomaticSync());

            scheduledExecutor.scheduleAtFixedRate(() -> {
                        if(Configuration.getConfiguration().isAutomaticSync()) {
                            runSync(bwClient);
                        }
                    },
                    1,
                    5,
                    TimeUnit.MINUTES
            );
            JCheckBoxMenuItem enableServer = new JCheckBoxMenuItem(RESOURCE_BUNDLE.getString("menuItem.enableServer"));
            enableServer.addActionListener(ae -> {
                boolean newState = !Configuration.getConfiguration().isStartUnixDomainSocketServer();
                Configuration.getConfiguration().setStartUnixDomainSocketServer(newState);
            });
            enableServer.setState(Configuration.getConfiguration().isStartUnixDomainSocketServer());
            enableServerReference.set(enableServer);
            JMenu fileMenu = new JMenu(RESOURCE_BUNDLE.getString("menuItem.file"));
            JCheckBoxMenuItem allowAllAccess = new JCheckBoxMenuItem(RESOURCE_BUNDLE.getString("menuItem.allowAllAccess"));
            allowAllAccess.addActionListener(ae -> {
                boolean newState = !Configuration.getConfiguration().isAllowAllAccess();
                Configuration.getConfiguration().setAllowAllAccess(newState);
            });
            Configuration.getConfiguration().addObserver((name, value) -> {
                if(PROP_ALLOW_ALL_ACCESS.equals(name)) {
                    allowAllAccess.setState((boolean) value);
                }
            });
            allowAllAccess.setState(Configuration.getConfiguration().isAllowAllAccess());
            menuBar.add(fileMenu);
            fileMenu.add(refresh);
            fileMenu.add(automaticSync);
            fileMenu.add(loginAction);
            fileMenu.add(logoutAction);
            fileMenu.addSeparator();
            fileMenu.add(enableServer);
            fileMenu.add(allowAllAccess);
            fileMenu.addSeparator();
            fileMenu.add(exit);
            frame.setJMenuBar(menuBar);
            frame.setLayout(new BorderLayout());
            frame.add(new BitwardenMainPanel(loginAction, bwClient, menuBar), BorderLayout.CENTER);
            frame.add(new StatusBar(bwClient), BorderLayout.SOUTH);
            frame.setSize(1200, 800);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });

        Runnable unixDomainSocketServerStarter = () -> {
            JCheckBoxMenuItem jcbmi = enableServerReference.get();
            if(jcbmi != null) {
                SwingUtilities.invokeLater(() -> jcbmi.setState(Configuration.getConfiguration().isStartUnixDomainSocketServer()));
            }
            try {
                synchronized (udss) {
                    if (Configuration.getConfiguration().isStartUnixDomainSocketServer()) {
                        if(udss.get() == null) {
                            UnixDomainSocketServer server = new UnixDomainSocketServer(bwClient);
                            server.start();
                            udss.set(server);
                        }
                    } else {
                        if(udss.get() != null) {
                            UnixDomainSocketServer server = udss.get();
                            udss.set(null);
                            server.shutdown();
                        }
                    }
                }
            } catch (IOException ex) {
                LOG.log(System.Logger.Level.ERROR, (String) null, ex);
            }
        };

        Configuration.getConfiguration().addObserver((name, value) -> {
            if (PROP_START_UNIX_DOMAIN_SOCKET_SERVER.equals(name)) {
                unixDomainSocketServerStarter.run();
            }
        });

        unixDomainSocketServerStarter.run();

    }

    public static void runSync(BitwardenClient bwClient) {
        try {
            UtilUI.runOffTheEdt(
                    () -> bwClient.sync(),
                    () -> {
                    },
                    (exception) -> {
                        LOG.log(WARNING, "Failed to run sync", exception);
                    }
            );
        } catch (Throwable t) {
            // This should never be hit. It is just a last resort to prevent
            // throwables to transition beyond this point
            LOG.log(ERROR, "Scheduling of sync failed", t);
        }
    }
}
