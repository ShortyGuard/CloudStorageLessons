package lesson1.client;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.*;

public class Client {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public Client() throws IOException {
        socket = new Socket("localhost", 1235);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        runClient();
    }

    private void runClient() {
        JFrame frame = new JFrame("Cloud Storage");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        JTextField textField = new JTextField();

        JList<String> fileList = new JList<>();
        fileList.addListSelectionListener(a -> {
            textField.setText(fileList.getSelectedValue());
        });

        JButton uploadButton = new JButton("Upload");
        JButton downloadButton = new JButton("Download");
        JButton deleteButton = new JButton("Delete");

        frame.getContentPane().add(BorderLayout.NORTH, textField);
        frame.getContentPane().add(BorderLayout.CENTER, fileList);

        JPanel footerJPanel = new JPanel();
        footerJPanel.setLayout(new GridLayout(2, 1, 1, 2));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 3, 2, 1));
        buttonPanel.add(uploadButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(deleteButton);

        footerJPanel.add(buttonPanel);

        JTextField statusField = new JTextField();
        footerJPanel.add(statusField);

        frame.getContentPane().add(BorderLayout.SOUTH, footerJPanel);

        fillFileList(fileList);

        frame.setVisible(true);

        uploadButton.addActionListener(a -> {
            statusField.setText(sendFile(textField.getText()));
        });

        downloadButton.addActionListener(a -> {
            statusField.setText(downloadFile(textField.getText()));
        });

        deleteButton.addActionListener(a -> {
            statusField.setText(deleteFile(textField.getText()));
        });
    }

    private void fillFileList(JList<String> fileList) {
        DefaultListModel listModel = new DefaultListModel();
        try {
            Files.walk(Paths.get("client"))
                .filter(Files::isRegularFile)
                .forEach(it -> {
                    listModel.addElement(it.getFileName().toString());
                });
            fileList.setModel(listModel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String sendFile(String filename) {
        try {
            File file = new File("client" + File.separator + filename);
            if (file.exists()) {
                out.writeUTF("upload");
                out.writeUTF(filename);
                long length = file.length();
                out.writeLong(length);
                FileInputStream fis = new FileInputStream(file);
                int read = 0;
                byte[] buffer = new byte[256];
                while ((read = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                String status = in.readUTF();
                return status;
            } else {
                return "File is not exists";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Something error";
    }

    private String downloadFile(String filename) {
        try {
            out.writeUTF("download");
            out.writeUTF(filename);

            try {
                String answer = in.readUTF();
                if ("FILE".equals(answer)) {
                    File file = new File("downloads" + File.separator + filename);
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
                    return "DONE";
                } else {
                    return answer;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Something error";
    }

    private String deleteFile(String filename) {
        try {
            File file = new File("client" + File.separator + filename);
            if (file.exists()) {
                out.writeUTF("remove");
                out.writeUTF(filename);
                String status = in.readUTF();
                return status;
            } else {
                return "File is not exists";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Something error";
    }

    public static void main(String[] args) throws IOException {
        new Client();
    }
}