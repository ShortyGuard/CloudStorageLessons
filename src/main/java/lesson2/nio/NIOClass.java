package lesson2.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class NIOClass {
    public static void main(String[] args) throws IOException {
        Path path = Paths.get("client");
        Path path1 = Path.of("client" + File.separator + "1.txt");

//		path.toAbsolutePath().iterator().forEachRemaining(System.out::println);
//		System.out.println("1.txt exists: " + Files.exists(path1));

        // создание
        Path path2 = Path.of("client", "dir1", "dir2", "1.txt");
        Path pathDir2 = Path.of("client", "dir1", "dir2");
//		if (!Files.exists(path2)) {
//			Files.createFile(path2);
//		}

        // перемещение
//		Path path3 = Files.move(Path.of("client" + File.separator + "2.txt"), path2, StandardCopyOption.ATOMIC_MOVE);

        // копирование
//		Path path4 = Files.copy(Path.of("client" + File.separator + "3.txt"), path2, StandardCopyOption.REPLACE_EXISTING);

        // запись в файл
//		Files.writeString(path2, "new line", StandardOpenOption.WRITE);
//		Files.writeString(path2, "new line2", StandardOpenOption.APPEND);
//		Files.writeString(path2, "new line3", StandardOpenOption.CREATE);
//
//		// удаление файла
//		Files.delete(Path.of("client", "dir1"));
//
        // создание директорий
//		Files.createDirectories(Paths.get("client", "dir3", "dir4", "dir5/dir6"));

        // обход всего дерева
//		Files.walkFileTree(path, new FileVisitor<Path>() {
//			@Override
//			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
//				System.out.println("pre - " + dir.getFileName());
//				return FileVisitResult.CONTINUE;
//			}
//
//			@Override
//			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//				System.out.println("visit - " + file.getFileName());
//				return FileVisitResult.CONTINUE;
//			}
//
//			@Override
//			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
//				System.out.println("fail - " + file.getFileName());
//				return FileVisitResult.CONTINUE;
//			}
//
//			@Override
//			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
//				System.out.println("post - " + dir.getFileName());
//				return FileVisitResult.CONTINUE;
//			}
//		});

        // поиска файла
//		Files.walkFileTree(path, new SimpleFileVisitor<>() {
//			@Override
//			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//				if ("6.txt".equals(file.getFileName().toString())) {
//					System.out.println(file.getFileName() + " is founded. Paht: " + file.toAbsolutePath());
//					return FileVisitResult.TERMINATE;
//				}
//				return FileVisitResult.CONTINUE;
//			}
//		});


        // считывание файла (1ый способ)
//		Files.readAllLines(path2).forEach(System.out::println);
//		List<String> list = Files.readAllLines(path2);
//		for (String line : list) {
//			System.out.println(line);
//		}

        // считывание файла (2ой способ)
//		Files.newBufferedReader(path2)
//				.lines()
//				.forEach(System.out::println);

        // считывание файла (3ий способ)
//		byte[] bytes = Files.readAllBytes(path2);
//		for (byte b : bytes) {
//			System.out.print((char) b);
//		}

        RandomAccessFile raf = new RandomAccessFile("client" + File.separator + "1.txt", "rw");
        FileChannel fileChannel = raf.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(55);
        System.out.println(buffer);
        System.out.println(fileChannel.size());
        int bytesRead = fileChannel.read(buffer);
        System.out.println(buffer);

        while (bytesRead != -1) {
            buffer.flip(); // перевод в режим чтения из буфера
            while (buffer.hasRemaining()) {
                System.out.print((char) buffer.get());
            }
            buffer.clear();
            bytesRead = fileChannel.read(buffer);
            System.out.println("---");
        }

        String data = "new String hello wolrd";
        buffer.put(data.getBytes(StandardCharsets.UTF_8));

        buffer.flip();
        fileChannel.position(0);
        while (buffer.hasRemaining()) {
            fileChannel.write(buffer);
        }

        raf.close();
    }
}
