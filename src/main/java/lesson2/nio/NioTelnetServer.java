package lesson2.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class NioTelnetServer {
    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    public static final String LS_COMMAND = "\tls          view all files from current directory\n";
    public static final String MKDIR_COMMAND = "\tmkdir       view all files from current directory\n";
    public static final String CHANGE_NICKNAME_COMMAND = "\tnick        change nickname\n";

    private Map<String, SocketAddress> clients = new HashMap<>();

    public NioTelnetServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open(); // открыли
        server.bind(new InetSocketAddress(1234));
        server.configureBlocking(false); // ВАЖНО
        Selector selector = Selector.open();
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
        while (server.isOpen()) {
            selector.select();
            var selectionKeys = selector.selectedKeys();
            var iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                var key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        SocketAddress client = channel.getRemoteAddress();
        String nickname = "";
        int readBytes = channel.read(buffer);
        if (readBytes < 0) {
            channel.close();
            return;
        } else if (readBytes == 0) {
            return;
        }

        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        buffer.clear();

        // TODO: 05.03.2021
        // touch (имя файла) - создание файла
        // mkdir (имя директории) - создание директории
        // cd (path) - перемещение по дереву папок
        // rm (имя файла или папки) - удаление объекта
        // copy (src, target) - копирование файла
        // cat (имя файла) - вывод в консоль содержимого

        if (key.isValid()) {
            String command = sb.toString()
                .replace("\n", "")
                .replace("\r", "");
            if ("--help".equals(command)) {
                sendMessage(LS_COMMAND, selector, client);
                sendMessage(MKDIR_COMMAND, selector, client);
                sendMessage(CHANGE_NICKNAME_COMMAND, selector, client);
            } else if (command.startsWith("nick ")) {
                nickname = command.split(" ")[1];
                clients.put(nickname, client);
                System.out.println("Client [" + client.toString() + "] changes nickname on [" + nickname + "]");
            } else if ("ls".equals(command)) {
                sendMessage(getFilesList().concat("\n"), selector, client);
            } else if ("exit".equals(command)) {
                System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                channel.close();
                return;
            }
        }

        for (Map.Entry<String, SocketAddress> clientInfo : clients.entrySet()) {
            if (clientInfo.getValue().equals(client)) {
                nickname = clientInfo.getKey();
            }
        }
        sendName(channel, nickname);
    }

    private void sendName(SocketChannel channel, String nickname) throws IOException {
        if (nickname.isEmpty()) {
            nickname = channel.getRemoteAddress().toString();
        }
        channel.write(
            ByteBuffer.wrap(nickname
                .concat(">: ")
                .getBytes(StandardCharsets.UTF_8)
            )
        );
    }

    private String getFilesList() {
        return String.join("\t", new File("server").list());
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel())
                        .write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ, "some attach");
        channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
        sendName(channel, "");
    }

    public static void main(String[] args) throws IOException {
        new NioTelnetServer();
    }
}