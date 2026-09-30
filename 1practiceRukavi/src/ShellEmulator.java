import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
    private final Map<String, Consumer<String[]>> commands = new HashMap<>();
    public ShellEmulator(String vfsPath, String scriptPath) {
        initCommands();
        File vfsSource = new File(vfsPath);
        String vfsName = vfsSource.exists() ? vfsSource.getName() : "Unknown_VFS";

        initGui(vfsName);
        loadVfsFromDisk(vfsPath);

        if (scriptPath != null && !scriptPath.isEmpty()) {
            runStartupScript(scriptPath);
        }
    }

    private void initCommands() {
        commands.put("exit", args -> System.exit(0));
        commands.put("clear", args -> outputArea.setText(""));
        commands.put("ls", args -> executeLs());
        commands.put("cd", args -> parseAndRun(args, "cd requires an argument", this::executeCd));
        commands.put("uniq", args -> parseAndRun(args, "uniq requires a file name", this::executeUniq));
        commands.put("rm", args -> parseAndRun(args, "rm requires a file/directory name", this::executeRm));
        commands.put("vfs-save", args -> parseAndRun(args, "vfs-save requires a path", this::executeVfsSave));
        commands.put("vfs-load", this::executeVfsLoad);
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
                    if (!command.isEmpty()) processInput(command);
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
        Consumer<String[]> cmd = commands.get(parts[0]);

        if (cmd != null) {
            cmd.accept(parts);
        } else {
            print("Error: command not found: " + parts[0]);
        }
    }

    private void parseAndRun(String[] parts, String errorMsg, Consumer<String> action) {
        if (parts.length > 1) action.accept(parts[1]);
        else print("Error: " + errorMsg);
    }

    private void executeVfsLoad(String[] parts) {
        if (parts.length > 1) {
            loadVfsFromDisk(parts[1]);
            File vfsSource = new File(parts[1]);
            frame.setTitle("VFS: " + (vfsSource.exists() ? vfsSource.getName() : "Unknown_VFS"));
        } else print("Error: vfs-load requires a path");
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
        if (target != null && target.isDirectory) currentDir = target;
        else if (target != null && !target.isDirectory) print("Error: " + path + " is not a directory");
        else print("Error: directory not found");
    }

    private void executeUniq(String filename) {
        VfsNode target = resolvePath(filename);
        if (target != null && !target.isDirectory) {
            String prevLine = null;
            for (String line : target.content) {
                if (!line.equals(prevLine)) {
                    print(line);
                    prevLine = line;
                }
            }
        } else if (target != null && target.isDirectory) print("Error: " + filename + " is a directory");
        else print("Error: file not found");
    }

    private void executeRm(String name) {
        VfsNode target = resolvePath(name);
        if (target != null) {
            if (target == root) {
                print("Error: cannot remove root");
                return;
            }
            target.parent.children.remove(target);
            print(name + " removed from VFS");
        } else print("Error: not found");
    }

    private void executeVfsSave(String destPath) {
        try {
            saveNodeToDisk(root, new File(destPath));
            print("VFS saved to " + destPath);
        } catch (IOException e) {
            print("Error saving: " + e.getMessage());
        }
    }

    private void saveNodeToDisk(VfsNode node, File file) throws IOException {
        if (node.isDirectory) {
            file.mkdirs();
            for (VfsNode child : node.children) saveNodeToDisk(child, new File(file, child.name));
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
            print("Warning: VFS source not found. Created empty root.");
        }
    }

    private VfsNode buildTree(File realFile, VfsNode parentNode) {
        VfsNode node = new VfsNode(realFile.getName(), realFile.isDirectory(), parentNode);
        if (node.isDirectory) {
            File[] files = realFile.listFiles();
            if (files != null) {
                for (File f : files) node.children.add(buildTree(f, node));
            }
        } else {
            try {
                node.content = Files.readAllLines(realFile.toPath());
            } catch (IOException e) {
                System.out.println("Error reading: " + realFile.getAbsolutePath());
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
        if (!file.exists() || file.isDirectory()) {
            print("Script not found: " + scriptPath);
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath())) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("//")) processInput(line);
            }
        } catch (IOException e) {
            print("Error reading script");
        }
    }

    public static void main(String[] args) {
        String vfsPath = args.length > 0 ? args[0] : "tests/minimal_vfs_folder";
        String scriptPath = args.length > 1 ? args[1] : "";
        SwingUtilities.invokeLater(() -> new ShellEmulator(vfsPath, scriptPath));
    }
}