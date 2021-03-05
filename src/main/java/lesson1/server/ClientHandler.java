package lesson1.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Обработчик входящих клиентов
 */
public class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println("Start new ClientHandler");
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {
            while (true) {
                String command = in.readUTF();
                if ("upload".equals(command)) {
                    try {
                        File file = new File("server" + File.separator + in.readUTF());
                        if (!file.exists()) {
                            file.createNewFile();
                        }
                        long size = in.readLong();
                        FileOutputStream fos = new FileOutputStream(file);
                        byte[] buffer = new byte[256];
                        for (int i = 0; i < (size + 255) / 256; i++) { // FIXME
                            int read = in.read(buffer);
                            fos.write(buffer, 0, read);
                        }
                        fos.close();
                        out.writeUTF("DONE");
                    } catch (Exception e) {
                        out.writeUTF("ERROR");
                    }
                } else if ("download".equals(command)) {
                    try {
                        File file = new File("server" + File.separator + in.readUTF());
                        if (!file.exists()) {
                            out.writeUTF("FILE NOT FOUND");
                        } else {
                            out.writeUTF("FILE");
                            long size = file.length();
                            out.writeLong(size);

                            FileInputStream fis = new FileInputStream(file);

                            byte[] buffer = new byte[256];
                            for (int i = 0; i < (size + 255) / 256; i++) {
                                int read = fis.read(buffer);
                                out.write(buffer, 0, read);
                            }
                            fis.close();
                        }
                    } catch (Exception e) {
                        out.writeUTF("ERROR");
                    }
                } else if ("remove".equals(command)) {
                    try {
                        File file = new File("server" + File.separator + in.readUTF());
                        if (file.exists()) {
                            file.delete();
                            out.writeUTF("DONE");
                        } else {
                            out.writeUTF("FILE NOT FOUND");
                        }
                    } catch (IOException e) {
                        out.writeUTF("ERROR");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("ClientHandler stopped");
    }
}
