/*
 * Copyright 2025 matthias.
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
package eu.doppelhelix.app.bitwardenagent.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.SwingWorker;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignO;
import org.kordamp.ikonli.swing.FontIcon;

public class UtilUI {

    public static final ImageIcon FOLDER_ICON = createIcon(MaterialDesignF.FOLDER_OUTLINE, 16);
    public static final ImageIcon OFFICE_BUILDING_ICON = createIcon(MaterialDesignO.OFFICE_BUILDING_OUTLINE, 16);
    public static final ImageIcon FOLDER_NETWORK_ICON = createIcon(MaterialDesignF.FOLDER_NETWORK_OUTLINE, 16);

    public static <T> void runOffTheEdt(Callable<T> runOffEdt, Consumer<T> runOnEdt, Consumer<Throwable> errorHandler) {
        new SwingWorker<T, T>() {
            @Override
            protected T doInBackground() throws Exception {
                return runOffEdt.call();
            }

            @Override
            protected void done() {
                T result;
                try {
                    result = get();
                } catch (ExecutionException ex) {
                    if(errorHandler != null) {
                        errorHandler.accept(ex.getCause());
                    }
                    return;
                } catch (InterruptedException | CancellationException ex) {
                    if(errorHandler != null) {
                        errorHandler.accept(ex);
                    }
                    return;
                }
                if(runOnEdt != null) {
                    runOnEdt.accept(result);
                }
            }
        }.execute();
    }

    public static <T> void runOffTheEdt(ThrowingRunnable runOffEdt, Runnable runOnEdt, Consumer<Throwable> errorHandler) {
        new SwingWorker<Object, Object>() {
            @Override
            protected T doInBackground() throws Exception {
                runOffEdt.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (ExecutionException ex) {
                    if(errorHandler != null) {
                        errorHandler.accept(ex.getCause());
                    }
                    return;
                } catch (InterruptedException | CancellationException ex) {
                    if(errorHandler != null) {
                        errorHandler.accept(ex);
                    }
                    return;
                }
                if(runOnEdt != null) {
                    runOnEdt.run();
                }
            }
        }.execute();
    }


    public static String escapeXml(String input) {
        return input
                .replace("&", "&auml;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public interface ThrowingRunnable {
        public void run() throws Exception;
    }

    public static ImageIcon createIcon(Ikon ikon, int size) {
        FontIcon fi = FontIcon.of(ikon);
        fi.setIconSize(size);
        return fi.toImageIcon();
    }

    public static String emptyNullToSpace(String input) {
        if(input == null || input.isEmpty()) {
            return " ";
        } else {
            return input;
        }
    }
}
