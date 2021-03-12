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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NioTelnetServer {
    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    public static final String LS_COMMAND = "\tls          view all files from current directory\n\r";
    public static final String MKDIR_COMMAND = "\tmkdir       create directory\n\r";
    public static final String CHANGE_NICKNAME_COMMAND = "\tnick        change nickname\n\r";
    public static final String TOUCH_COMMAND = "\ttouch          create empty file\n\r";
    public static final String CD_COMMAND = "\tcd          change directory\n\r";
    public static final String RM_COMMAND = "\trm          delete file or directory recursively\n\r";
    public static final String COPY_COMMAND = "\trm          copy source to destination\n\r";
    public static final String CAT_COMMAND = "\trm          cat file\n\r";

    private Map<String, SocketAddress> clients = new HashMap<>();
    private String currentDir = "server";

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

        if (key.isValid()) {
            String command = sb.toString()
                .replace("\n", "")
                .replace("\r", "");
            System.out.println("Received command: " + command);

            if ("--help".equals(command)) {
                sendMessage(LS_COMMAND, selector, client);
                sendMessage(MKDIR_COMMAND, selector, client);
                sendMessage(CHANGE_NICKNAME_COMMAND, selector, client);
                sendMessage(TOUCH_COMMAND, selector, client);
                sendMessage(CD_COMMAND, selector, client);
                sendMessage(RM_COMMAND, selector, client);
                sendMessage(COPY_COMMAND, selector, client);
                sendMessage(CAT_COMMAND, selector, client);
            } else if (command.startsWith("copy ")) {
                sendMessage(copyCommand(command).concat("\n\r"), selector, client);
            } else if (command.startsWith("cat ")) {
                sendMessage(catCommand(command, selector, client).concat("\n\r"), selector, client);
            } else if (command.startsWith("cd ")) {
                sendMessage(cdCommand(command).concat("\n\r"), selector, client);
            } else if (command.startsWith("rm ")) {
                sendMessage(rmCommand(command).concat("\n\r"), selector, client);
            } else if (command.startsWith("touch ")) {
                sendMessage(touchCommand(command).concat("\n\r"), selector, client);
            } else if (command.startsWith("mkdir ")) {
                sendMessage(mkdirCommand(command).concat("\n\r"), selector, client);
            } else if (command.startsWith("nick ")) {
                nickname = command.split(" ")[1];
                clients.put(nickname, client);
                System.out.println("Client [" + client.toString() + "] changes nickname on [" + nickname + "]");
            } else if ("ls".equals(command)) {
                sendMessage(getFilesList().concat("\n\r"), selector, client);
            } else if ("exit".equals(command)) {
                System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                channel.close();
                return;
            } else {
                System.out.println("Ignore unknown command: " + command);
            }
        }

        for (Map.Entry<String, SocketAddress> clientInfo : clients.entrySet()) {
            if (clientInfo.getValue().equals(client)) {
                nickname = clientInfo.getKey();
            }
        }
        sendName(channel, nickname);
    }

    private String catCommand(String command, Selector selector, SocketAddress client) {
        String pathToCat = command.substring(4).trim();
        if (!pathToCat.startsWith("/")) {
            pathToCat = currentDir.concat("/").concat(pathToCat);
        }
        if (pathToCat.endsWith("/")) {
            pathToCat.substring(0, pathToCat.length() - 1);
        }
        Path catPath = Path.of(pathToCat);
        if (!Files.exists(catPath) || Files.isDirectory(catPath)) {
            return "File not found: ".concat(pathToCat);
        }

        try {
            byte[] bytes = Files.readAllBytes(catPath);
            for (byte b : bytes) {
                System.out.print((char) b);
            }
            for (SelectionKey key : selector.keys()) {
                if (key.isValid() && key.channel() instanceof SocketChannel) {
                    if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                        ((SocketChannel) key.channel())
                            .write(ByteBuffer.wrap(bytes));
                    }
                }
            }
        } catch (IOException e) {
            return "Couldn't read file to cat: ".concat(pathToCat);
        }

        return "";
    }

    private String copyCommand(String command) {
        // сначала определим пути исходного и целевого файла
        String arguments = command.substring(5).trim();
        String sourceFile;
        String destinationFile;
        // определим сложние пути в кавычках
        List<String> split = Arrays.stream(arguments.split("\""))
            .filter(str -> str.trim().length() > 0).collect(Collectors.toList());
        // если было без кавычек, то разделение пробелами
        if (split.size() == 1) {
            split = Arrays.asList(arguments.split("[ ]+"));
        }
        if (split.size() == 2) {
            sourceFile = split.get(0).trim();
            destinationFile = split.get(1).trim();
        } else {
            return "Couldn't copy. Wrong parameters: ".concat(arguments);
        }

        if (!sourceFile.startsWith("/")) {
            sourceFile = currentDir.concat("/").concat(sourceFile);
        }
        if (sourceFile.endsWith("/")) {
            sourceFile.substring(0, sourceFile.length() - 1);
        }
        Path sourcePath = Path.of(sourceFile);
        if (!destinationFile.startsWith("/")) {
            destinationFile = currentDir.concat("/").concat(destinationFile);
        }
        if (destinationFile.endsWith("/")) {
            destinationFile.substring(0, destinationFile.length() - 1);
        }
        Path destinationPath = Path.of(destinationFile);

        System.out.println("Copy from:  " + sourceFile + " to: " + destinationFile);
        try {
            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return "Couldn't copy : ".concat(arguments).concat(" Exception: ").concat(e.getMessage());
        }
        return "Copied.";
    }

    private String rmCommand(String command) {
        String pathToRemove = command.substring(3).trim();
        if (!pathToRemove.startsWith("/")) {
            pathToRemove = currentDir.concat("/").concat(pathToRemove);
        }
        if (pathToRemove.endsWith("/")) {
            pathToRemove.substring(0, pathToRemove.length() - 1);
        }
        Path rmPath = Path.of(pathToRemove);
        if (!Files.isExecutable(rmPath)) {
            return "File not found.";
        } else if (Files.isDirectory(rmPath)) {
            // обход дерева, рекурсивно все удаляем
            try {
                Files.walkFileTree(rmPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        // удаляем файлы, чтобы очистить директории для последующего удаления
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        // удаляем директорию после обхода этой директории (уже очистили)
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                return "Couldn't remove directory: ".concat(pathToRemove).concat(" Exception: ").concat(e.getMessage());
            }
            return "Directory: ".concat(pathToRemove).concat(" removed");
        } else {
            try {
                if (Files.deleteIfExists(rmPath)) {
                    return "File removed";
                }
            } catch (IOException e) {
                "Couldn't remove file: ".concat(pathToRemove).concat(" Exception: ").concat(e.getMessage());
            }
        }

        return "Couldn't remove file: ".concat(pathToRemove);
    }

    private String cdCommand(String command) {
        String commandPath = command.substring(3).trim();

        if (!commandPath.startsWith("/")) {
            commandPath = currentDir.concat("/").concat(commandPath);
        }
        if (commandPath.endsWith("/")) {
            commandPath.substring(0, commandPath.length() - 1);
        }
        Path newPath = Path.of(commandPath);
        if (Files.exists(newPath) && Files.isDirectory(newPath)) {
            currentDir = commandPath;
            return "Directory changed";
        }
        return "Bad directory path: " + commandPath;
    }

    private String touchCommand(String command) {
        String fileName = command.substring(6).trim();
        // создадим файл с полученным именем
        Path filePath = Paths.get(currentDir + File.separator + fileName);
        try {
            Files.createFile(filePath); // создадим с DEFAULT_CREATE_OPTIONS
        } catch (IOException e) {
            return "Couldn't create file: ".concat(fileName);
        }
        return "Created file: ".concat(fileName);
    }

    private String mkdirCommand(String command) {
        String dirName = command.substring(6).trim();
        // создадим директорию с полученным именем
        Path filePath = Paths.get(currentDir + File.separator + dirName);
        try {
            Files.createDirectory(filePath);
        } catch (IOException e) {
            return "Couldn't create directory: ".concat(dirName);
        }
        return "Created directory: ".concat(dirName);
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
        return String.join("\t", new File(currentDir).list());
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        System.out.println("Send butes: " + bytes);
        System.out.println("Send message: " + new String(bytes));
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel())
                        .write(ByteBuffer.wrap(bytes));
                }
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ, "some attach");
        channel.write(ByteBuffer.wrap("Hello user!\n\r".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info\n\r".getBytes(StandardCharsets.UTF_8)));
        sendName(channel, "");
    }

    public static void main(String[] args) throws IOException {
        new NioTelnetServer();
    }
}