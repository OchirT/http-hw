import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        int numThreads = 64;
        Server server = new Server(numThreads);
        server.start();
    }

}
