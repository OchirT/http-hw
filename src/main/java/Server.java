import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
    public static final String GET = "GET";
    public static final String POST = "POST";
    private static Map<String, Map<String, HandlerRequest>> handlerMap = new ConcurrentHashMap<>();
    static final List<String> allowedMethods = List.of(GET, POST);
    private static Request request;

    public static void addHandler(String method, String path, HandlerRequest handler) {
        if (handlerMap.containsKey(method)) {
            handlerMap.get(method).put(path, handler);
        } else {
            Map<String, HandlerRequest> map = new ConcurrentHashMap<>();
            map.put(path, handler);
            handlerMap.put(method, map);
        }
    }

    public final int numThreads;
    public static final int Port = 9999;

    public Server(int numThreads) {
        this.numThreads = numThreads;
    }

    public void start() {

        ExecutorService service = Executors.newFixedThreadPool(numThreads);
        try (final var serverSocket = new ServerSocket(Port)) {
            while (true) {
                var socket = serverSocket.accept();
                service.submit(() -> {
                    try {
                        logic(socket);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logic(Socket socket) throws Exception {
        try (var in = new BufferedInputStream(socket.getInputStream());
             var out = new BufferedOutputStream(socket.getOutputStream())) {
            Request request = getRequest(in, out);
            System.out.println(request.getQueryParams());
            System.out.println(request.getPostParams());
            HandlerRequest handlerRequest1 = handlerMap.get(request.getMethod()).get(request.getPath());
            handlerRequest1.handle(request, out);
        }

    }

    public static void badRequest(BufferedOutputStream out) {
        try {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-lenght: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public static Request getRequest(BufferedInputStream in, BufferedOutputStream out) throws Exception {
        while (true) {
            final var limit = 4096;
            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);


            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                badRequest(out);
                continue;
            }


            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                continue;
            }

            final var method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                badRequest(out);
                continue;
            }
            System.out.println(method);

            var path = requestLine[1].contains("?") ? requestLine[1].substring(0,requestLine[1].indexOf("?")) : requestLine[1];
            if (!path.startsWith("/")) {
                badRequest(out);
            }
            System.out.println(path);


            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                badRequest(out);
                continue;
            }

            in.reset();

            in.skip(headersStart);

            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            System.out.println(headers);


            final var contentType = extractHeader(headers, "Content-Type");
            final var contentLength = extractHeader(headers, "Content-Length");
            URI url = new URI(requestLine[1]);
            List<NameValuePair> params = URLEncodedUtils.parse(url,StandardCharsets.UTF_8);
            List<NameValuePair>postParams = getPostParams(in,contentType,contentLength);
            request = new Request(method,path,params,postParams);
            return request;
        }
    }

    public static List<NameValuePair> getPostParams(BufferedInputStream in, Optional<String> contentType, Optional<String> contentLength) throws IOException {
        List<NameValuePair> params = null;
        if (contentType.isPresent()) {
            if (contentType.get().equals(URLEncodedUtils.CONTENT_TYPE)) {
                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    final var bodyBytes = in.readNBytes(length);
                    String body = new String(bodyBytes);
                    params = URLEncodedUtils.parse(body, StandardCharsets.UTF_8);
                }
            }
        }
        return params;
    }

    private static Optional<String> extractHeader (List < String > headers, String header){
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static int indexOf ( byte[] array, byte[] target, int start, int max){
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public void sendResponse (Request request, BufferedOutputStream out) throws IOException {
        final Path filePath = Path.of(".", "public", request.getPath());
        final String mimeType = Files.probeContentType(filePath);

        if (!handlerMap.get(request.getMethod()).containsKey(request.getPath())) {
            badRequest(out);
        }
        if (request.getPath().equals("/classic.html")) {
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
        }
        final var length = Files.size(filePath);

        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }



}
