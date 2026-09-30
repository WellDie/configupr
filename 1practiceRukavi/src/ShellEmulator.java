import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

class VfsNode {
    String name;
    boolean isDirectory;
    List<VfsNode> children;
    List<String> content;
    VfsNode parent;

    public VfsNode(String name, boolean isDirectory, VfsNode parent) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.parent = parent;
        this.children = new ArrayList<>();
        this.content = new ArrayList<>();
    }
}

public class ShellEmulator {
    private JFrame frame;
    private JTextArea outputArea;
    private JTextField inputField;
    private VfsNode root;
    private VfsNode currentDir;

    public ShellEmulator(String vfsPath, String scriptPath) {
        System.out.println("Debug: VFS Path = " + vfsPath);
        System.out.println("Debug: Script Path = " + scriptPath);

        File vfsSource = new File(vfsPath);
        String vfsName = vfsSource.exists() ? vfsSource.getName() : "Unknown_VFS";

        initGui(vfsName);
        loadVfsFromDisk(vfsPath);

        if (scriptPath != null && !scriptPath.isEmpty()) {
            runStartupScript(scriptPath);
        }
    }

    private void initGui(String vfsName) {
        frame = new JFrame("VFS: " + vfsName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        outputArea.setBackground(Color.BLACK);
        outputArea.setForeground(Color.LIGHT_GRAY);

        frame.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        frame.add(createInputField(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JTextField createInputField() {
        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        inputField.setBackground(Color.DARK_GRAY);
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String command = inputField.getText().trim();
                    inputField.setText("");
                    if (!command.isEmpty()) {
                        processInput(command);
                    }
                }
            }
        });
        return inputField;
    }

    private void print(String text) {
        outputArea.append(text + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void processInput(String input) {
        print("> " + input);
        String[] parts = input.split("\\s+");
        String command = parts[0];

        switch (command) {
            case "exit": System.exit(0); break;
            case "clear": outputArea.setText(""); break;
            case "ls": executeLs(); break;
            case "cd": parseAndRun(parts, "cd requires an argument", this::executeCd); break;
            case "uniq": parseAndRun(parts, "uniq requires a file name", this::executeUniq); break;
            case "rm": parseAndRun(parts, "rm requires a file/directory name", this::executeRm); break;
            case "vfs-save": parseAndRun(parts, "vfs-save requires a path", this::executeVfsSave); break;
            case "vfs-load": executeVfsLoad(parts); break;
            default: print("Error: command not found: " + command); break;
        }
    }

    private void parseAndRun(String[] parts, String errorMsg, java.util.function.Consumer<String> action) {
        if (parts.length > 1) {
            action.accept(parts[1]);
        } else {
            print("Error: " + errorMsg);
        }
    }

    private void executeVfsLoad(String[] parts) {
        if (parts.length > 1) {
            loadVfsFromDisk(parts[1]);
            File vfsSource = new File(parts[1]);
            frame.setTitle("VFS: " + (vfsSource.exists() ? vfsSource.getName() : "Unknown_VFS"));
        } else {
            print("Error: vfs-load requires a path");
        }
    }

    private void executeLs() {
        if (currentDir == null) return;
        for (VfsNode child : currentDir.children) {
            String type = child.isDirectory ? "[DIR] " : "[FILE] ";
            print(type + child.name);
        }
    }

    private void executeCd(String path) {
        VfsNode target = resolvePath(path);
        if (target != null && target.isDirectory) {
            currentDir = target;
        } else if (target != null && !target.isDirectory) {
            print("Error: " + path + " is not a directory");
        } else {
            print("Error: directory not found");
        }
    }

    private void executeUniq(String filename) {
        VfsNode target = resolvePath(filename);
        if (target != null && !target.isDirectory) {
            String previousLine = null;
            for (String line : target.content) {
                if (!line.equals(previousLine)) {
                    print(line);
                    previousLine = line;
                }
            }
        } else if (target != null && target.isDirectory) {
            print("Error: " + filename + " is a directory");
        } else {
            print("Error: file not found");
        }
    }

    private void executeRm(String name) {
        VfsNode target = resolvePath(name);
        if (target != null) {
            if (target == root) {
                print("Error: cannot remove root directory");
                return;
            }
            target.parent.children.remove(target);
            print(name + " removed from VFS memory");
        } else {
            print("Error: file or directory not found");
        }
    }

    private void executeVfsSave(String destPath) {
        File destFile = new File(destPath);
        try {
            saveNodeToDisk(root, destFile);
            print("VFS successfully saved to " + destPath);
        } catch (IOException e) {
            print("Error saving VFS: " + e.getMessage());
        }
    }

    private void saveNodeToDisk(VfsNode node, File file) throws IOException {
        if (node.isDirectory) {
            file.mkdirs();
            for (VfsNode child : node.children) {
                saveNodeToDisk(child, new File(file, child.name));
            }
        } else {
            Files.write(file.toPath(), node.content);
        }
    }

    private void loadVfsFromDisk(String path) {
        File file = new File(path);
        if (file.exists() && file.isDirectory()) {
            root = buildTree(file, null);
            currentDir = root;
            print("VFS loaded from " + path);
        } else {
            root = new VfsNode("root", true, null);
            currentDir = root;
            print("Warning: VFS source not found or is not a directory. Created empty root.");
        }
    }

    private VfsNode buildTree(File realFile, VfsNode parentNode) {
        VfsNode node = new VfsNode(realFile.getName(), realFile.isDirectory(), parentNode);
        if (node.isDirectory) {
            File[] files = realFile.listFiles();
            if (files != null) {
                for (File f : files) {
                    node.children.add(buildTree(f, node));
                }
            }
        } else {
            try {
                node.content = Files.readAllLines(realFile.toPath());
            } catch (IOException e) {
                System.out.println("Error reading file: " + realFile.getAbsolutePath());
            }
        }
        return node;
    }

    private VfsNode resolvePath(String path) {
        if ("/".equals(path)) return root;

        VfsNode node = path.startsWith("/") ? root : currentDir;
        for (String part : path.split("/")) {
            node = stepToNode(node, part);
            if (node == null) return null;
        }
        return node;
    }

    private VfsNode stepToNode(VfsNode current, String part) {
        if (part.isEmpty() || ".".equals(part)) return current;
        if ("..".equals(part)) return current.parent != null ? current.parent : current;
        return findChild(current, part);
    }

    private VfsNode findChild(VfsNode parent, String name) {
        for (VfsNode child : parent.children) {
            if (child.name.equals(name)) return child;
        }
        return null;
    }

    private void runStartupScript(String scriptPath) {
        File file = new File(scriptPath);
        if (file.exists() && !file.isDirectory()) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("//")) {
                        processInput(line);
                    }
                }
            } catch (IOException e) {
                print("Error reading startup script");
            }
        } else {
            print("Startup script not found: " + scriptPath);
        }
    }

    public static void main(String[] args) {
        String vfsPath = args.length > 0 ? args[0] : "my_vfs_folder";
        String scriptPath = args.length > 1 ? args[1] : "";

        SwingUtilities.invokeLater(() -> new ShellEmulator(vfsPath, scriptPath));
    }
}