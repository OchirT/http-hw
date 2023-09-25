import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class Server {
    public final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js");

    public void start() {
        ExecutorService service = Executors.newFixedThreadPool(64);
        try (final var serverSocket = new ServerSocket(9999)) {
            while (true) {
                final var socket = serverSocket.accept();
                service.submit(() -> {
                    logic(socket);
                });
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Runnable logic(Socket socket) {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            while (true) {
                final var requestLine = in.readLine();
                final var parts = requestLine.split(" ");

                if (parts.length !=3) {
                    continue;
                }
                final var path = parts[1];
                if (!validPaths.contains(path)) {
                    notFound(out);
                    continue;
                }
                final var filePath = Path.of(".", "public", path);
                final var mimeType = Files.probeContentType(filePath);

                if(path.equals("/classic.html")) {
                    forClassic(filePath, mimeType, out);
                    continue;
                }

                final var lenght = Files.size(filePath);
                isOk(mimeType, lenght, filePath, out);

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    public static void notFound(BufferedOutputStream out) {
        try {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-lenght: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
                    ).getBytes());
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void forClassic(Path filePath, String mimeType, BufferedOutputStream out) {
        try {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
                    ).getBytes());
            out.write(content);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void isOk(String mimeType, long length, Path filePath, BufferedOutputStream out) {
        try {
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();

        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

}
