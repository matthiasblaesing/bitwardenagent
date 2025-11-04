
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class Packaging {

    private static final Pattern JDK_DIR_NAME = Pattern.compile("(.*?)-([^-]+)-([^-]+)");

    public static void main(String[] args) throws Exception {
        File baseDir = new File(args[0]);
        String artifactId = args[1];

        File jlinkBinary;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            jlinkBinary = new File(System.getProperty("java.home"), "bin/jlink.exe");
        } else {
            jlinkBinary = new File(System.getProperty("java.home"), "bin/jlink");
        }

        // Can be queries using jdeps -s <Path_to_IdoitVSphereSync.jar>
        List<String> modules = new ArrayList<>();
        modules.add("java.base");
        modules.add("java.datatransfer");
        modules.add("java.desktop");
        modules.add("java.logging");
        modules.add("java.net.http");
        modules.add("java.naming");
        modules.add("java.xml");
        modules.add("jdk.httpserver");
        modules.add("jdk.crypto.ec");
        modules.add("jdk.accessibility");

        String DIR_DISTRO = "dist";

        File outputDir = new File(baseDir, "target/" + DIR_DISTRO);

        if (Files.exists(outputDir.toPath())) {
            Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    if (e == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        }

        // The JDKs are normaly packaged as ZIP with a single top folder, that holds the
        // JDK. This topFolder needs to be found
        Path jdksPath = Path.of(baseDir.getAbsolutePath(), "target/jdks/");
        for (Path jdkParentDir : Files.list(jdksPath).toList()) {
            File[] jdkDirCandidates = Files.list(jdkParentDir).map(p -> p.toFile()).toArray(File[]::new);
            if (jdkDirCandidates == null) {
                continue;
            }
            for (File jdkDirCandidate : jdkDirCandidates) {
                for(String jmodsPath: new String[]{"jmods", "Contents/Home/jmods"}) {
                    String outputName = JDK_DIR_NAME.matcher(jdkParentDir.getFileName().toString())
                            .replaceAll("bitwardenagent-$2-$3");
                    File platformOutputDir = new File(outputDir, outputName);
                    File platformOutputZip = new File(outputDir, outputName + ".zip");
                    File jmods = new File(jdkDirCandidate, jmodsPath);
                    if (jmods.exists()) {
                        System.out.println("Building with Modules: " + jmods.getAbsolutePath());
                        ProcessBuilder pb = new ProcessBuilder(
                                jlinkBinary.getAbsolutePath(),
                                "--compress", "zip-9",
                                "--no-header-files",
                                "--no-man-pages",
                                "--output", platformOutputDir.getAbsolutePath(),
                                "--module-path", jmods.getAbsolutePath(),
                                "--add-modules", modules.stream().collect(Collectors.joining(","))
                        );

                        pb.inheritIO();

                        pb.start().waitFor();

                        // Copy FAT jar
                        Files.copy(
                                new File(baseDir, "target/" + artifactId + ".jar").toPath(),
                                new File(platformOutputDir, "lib/" + artifactId + ".jar").toPath()
                        );

                        if(jdkDirCandidate.getParentFile().getName().contains("-windows-")) {
                            try(FileOutputStream fos = new FileOutputStream(new File(platformOutputDir, "bitwardenagent.cmd"));
                                    OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                                osw.write("start \"\" \"%~dp0bin\\javaw.exe\" -jar \"%~dp0lib\\bitwardenagent.jar\"");
                            }
                        } else {
                            File startScript = new File(platformOutputDir, "bitwardenagent.sh");
                            try(FileOutputStream fos = new FileOutputStream(startScript);
                                    OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                                osw.write(
                                        """
#!/bin/sh

cwd=`pwd`
exec_dir=$(dirname "$0")
cd "$exec_dir"
exec_absolute_dir=`pwd`
cd $cwd

"$exec_absolute_dir/bin/java" -jar "$exec_absolute_dir/lib/bitwardenagent.jar"
                                        """
                                );
                            }
                            Files.setPosixFilePermissions(
                                    startScript.toPath(),
                                    EnumSet.of(
                                            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
                                            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                                            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                                    )
                            );
                        }

                        Map<String, Object> env = new HashMap<>();
                        env.put("create", "true");
                        env.put("enablePosixFileAttributes", true);
                        try (FileSystem zipfs = FileSystems.newFileSystem(platformOutputZip.toPath(), env)) {
                            Files.walk(platformOutputDir.toPath())
                                    .forEach(p -> {
                                        Path relativePath = platformOutputDir.toPath().relativize(p);
                                        Path zipPath = zipfs.getPath("/bitwardenagent/", relativePath.toString());
                                        try {
                                            if (Files.isDirectory(p)) {
                                                Files.createDirectories(zipPath);
                                            } else {
                                                Files.copy(p, zipPath);
                                                if ("bin".equals(zipPath.getParent().getFileName().toString())
                                                        || "bitwardenagent.cmd".equals(zipPath.getFileName().toString())
                                                        || "bitwardenagent.sh".equals(zipPath.getFileName().toString())) {
                                                    Files.setPosixFilePermissions(
                                                            zipPath,
                                                            EnumSet.of(
                                                                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
                                                                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                                                                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                                                            )
                                                    );
                                                }
                                            }
                                        } catch (IOException ex) {
                                            System.getLogger(Packaging.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                                        }
                                    });
                        }

                        break;
                    }
                }
            }
        }
    }
}
