package com.oAT.agent.attach;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AttachAgent {
    public AttachAgent(String[] args) throws Exception {
        List<JavaProcess> processes = hasPid(args) ? new ArrayList<JavaProcess>() : listJavaProcesses();
        String pid = resolvePid(args, processes);
        String agentArgs = args != null && args.length > 1 ? args[1] : "";
        attach(pid, findAgentJar(), agentArgs);
    }

    private boolean hasPid(String[] args) {
        return args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty();
    }

    private String resolvePid(String[] args, List<JavaProcess> processes) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            return args[0].trim();
        }
        if (processes.isEmpty()) {
            throw new IllegalStateException("No attachable Java process found");
        }
        System.out.println("Attachable Java processes:");
        for (int i = 0; i < processes.size(); i++) {
            JavaProcess process = processes.get(i);
            System.out.println((i + 1) + ". " + process.pid + " " + process.displayName);
        }
        System.out.print("Select process number: ");
        Scanner scanner = new Scanner(System.in);
        int selected = scanner.nextInt();
        if (selected < 1 || selected > processes.size()) {
            throw new IllegalArgumentException("Invalid process number: " + selected);
        }
        return processes.get(selected - 1).pid;
    }

    private List<JavaProcess> listJavaProcesses() throws Exception {
        List<JavaProcess> result = new ArrayList<JavaProcess>();
        Class<?> vmClass = loadVirtualMachineClass();
        Method listMethod = vmClass.getMethod("list");
        List<?> descriptors = (List<?>) listMethod.invoke(null);
        for (Object descriptor : descriptors) {
            Method idMethod = descriptor.getClass().getMethod("id");
            Method displayNameMethod = descriptor.getClass().getMethod("displayName");
            String id = String.valueOf(idMethod.invoke(descriptor));
            String displayName = String.valueOf(displayNameMethod.invoke(descriptor));
            result.add(new JavaProcess(id, displayName));
        }
        return result;
    }

    private void attach(String pid, File agentJar, String agentArgs) throws Exception {
        Object vm = null;
        Class<?> vmClass = loadVirtualMachineClass();
        try {
            Method attachMethod = vmClass.getMethod("attach", String.class);
            vm = attachMethod.invoke(null, pid);
            Method loadAgentMethod = vmClass.getMethod("loadAgent", String.class, String.class);
            loadAgentMethod.invoke(vm, agentJar.getAbsolutePath(), agentArgs == null ? "" : agentArgs);
            System.out.println("oAT agent attached to process " + pid + ", agent=" + agentJar.getAbsolutePath());
        } finally {
            if (vm != null) {
                Method detachMethod = vmClass.getMethod("detach");
                detachMethod.invoke(vm);
            }
        }
    }

    private Class<?> loadVirtualMachineClass() throws Exception {
        try {
            return Class.forName("com.sun.tools.attach.VirtualMachine");
        } catch (ClassNotFoundException ignored) {
            try {
                return Class.forName("com.ibm.tools.attach.VirtualMachine");
            } catch (ClassNotFoundException ignoredAgain) {
                return loadVirtualMachineClassFromToolsJar();
            }
        }
    }

    private Class<?> loadVirtualMachineClassFromToolsJar() throws Exception {
        File toolsJar = findToolsJar();
        if (toolsJar == null || !toolsJar.isFile()) {
            throw new ClassNotFoundException("Attach API not found. Use a JDK instead of a JRE, or run with "
                    + "$JAVA_HOME/lib/tools.jar on classpath. java.home=" + System.getProperty("java.home"));
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{toolsJar.toURI().toURL()},
                AttachAgent.class.getClassLoader());
        return Class.forName("com.sun.tools.attach.VirtualMachine", true, loader);
    }

    private File findToolsJar() {
        String javaHome = System.getProperty("java.home");
        List<File> candidates = new ArrayList<File>();
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            File home = new File(javaHome);
            candidates.add(new File(home, "lib/tools.jar"));
            File parent = home.getParentFile();
            if (parent != null) {
                candidates.add(new File(parent, "lib/tools.jar"));
            }
        }
        String envJavaHome = System.getenv("JAVA_HOME");
        if (envJavaHome != null && !envJavaHome.trim().isEmpty()) {
            File home = new File(envJavaHome);
            candidates.add(new File(home, "lib/tools.jar"));
            File parent = home.getParentFile();
            if (parent != null) {
                candidates.add(new File(parent, "lib/tools.jar"));
            }
        }
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private File findAgentJar() {
        URL url = AttachAgent.class.getProtectionDomain().getCodeSource().getLocation();
        File file = new File(url.getFile());
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot locate agent jar from " + file.getAbsolutePath());
        }
        return file;
    }

    private static class JavaProcess {
        private final String pid;
        private final String displayName;

        private JavaProcess(String pid, String displayName) {
            this.pid = pid;
            this.displayName = displayName;
        }
    }
}
