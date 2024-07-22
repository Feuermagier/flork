package de.firemage.flork.compiler;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class InMemoryCompiler {
    public static Path compile(Path path) throws IOException {
        Map<String, String> sources = new HashMap<>();
        Files.walk(path).forEach(file -> {
            if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                try {
                    sources.put(path.relativize(file).toString(), Files.readString(file));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return compile(sources);
    }

    public static Path compile(Map<String, String> sources) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnosticCollector = new DiagnosticCollector<>();
        var fileManager = new VirtualFileManager(compiler.getStandardFileManager(diagnosticCollector, Locale.US, StandardCharsets.UTF_8));
        List<JavaFileObject> fileObjects = sources.entrySet().stream().map(entry ->
                (JavaFileObject) new SimpleJavaFileObject(classNameToURI(entry.getKey()), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return entry.getValue();
                    }
                }
        ).toList();

        StringWriter output = new StringWriter();
        boolean successful = compiler.getTask(output, fileManager, diagnosticCollector, List.of("--release=21"), null, fileObjects).call();
        output.flush();

        String diagnostics = diagnosticCollector.getDiagnostics().stream().map(d -> d.toString()).collect(Collectors.joining("\n"));

        if (!successful) {
            throw new IOException("Compilation failed: \n" + diagnostics);
        }
        output.close();

        var writtenFiles = fileManager.getCompiledClasses();
        fileManager.close();

        var basePath = Files.createTempDirectory("compiled-classes");
        System.out.println(basePath);
        writtenFiles.forEach((key, value) -> {
            try {
                Files.write(basePath.resolve(key + ".class"), value);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        // List<URL> urls = writtenFiles.entrySet().stream().map(e -> {
        //     try {
        //         return URL.of(e.getKey(), new URLStreamHandler() {
        //             @Override
        //             protected URLConnection openConnection(URL url) throws IOException {
        //                 return new URLConnection(url) {
        //                     @Override
        //                     public void connect() throws IOException {
        //                     }
        //
        //                     @Override
        //                     public InputStream getInputStream() throws IOException {
        //                         return new ByteArrayInputStream(e.getValue());
        //                     }
        //                 };
        //             }
        //         });
        //     } catch (MalformedURLException ex) {
        //         throw new RuntimeException(ex);
        //     }
        // }).toList();

        return basePath;
    }

    public static URI classNameToURI(String className) {
        return URI.create("string:///" + className);
    }
}
