package com.earendil.pi;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * 轻量架构测试：扫描 src/main/java 的 import 语句，强制 package 依赖方向指向更低层的包。
 * 规则与 AGENTS.md / docs/agents/architecture.md 保持一致；新增 package 时同步更新允许表。
 */
public class ArchitectureTest {
    private static final String ROOT = "com.earendil.pi";

    private static final Map<String, Set<String>> ALLOWED = buildAllowed();

    private static Map<String, Set<String>> buildAllowed() {
        Map<String, Set<String>> rules = new HashMap<String, Set<String>>();
        rules.put("com.earendil.pi.internal", new HashSet<String>());
        rules.put("com.earendil.pi.session", allowed("com.earendil.pi", "com.earendil.pi.internal"));
        rules.put("com.earendil.pi.tool", allowed("com.earendil.pi", "com.earendil.pi.internal"));
        rules.put("com.earendil.pi.llm", allowed("com.earendil.pi", "com.earendil.pi.internal", "com.earendil.pi.tool"));
        rules.put("com.earendil.pi.context", allowed("com.earendil.pi", "com.earendil.pi.internal",
                "com.earendil.pi.session", "com.earendil.pi.tool", "com.earendil.pi.llm"));
        rules.put("com.earendil.pi.security", allowed("com.earendil.pi", "com.earendil.pi.internal", "com.earendil.pi.tool"));
        rules.put("com.earendil.pi.agent", allowed("com.earendil.pi", "com.earendil.pi.internal",
                "com.earendil.pi.session", "com.earendil.pi.tool", "com.earendil.pi.llm",
                "com.earendil.pi.context", "com.earendil.pi.security"));
        rules.put(ROOT, allowed("com.earendil.pi", "com.earendil.pi.internal", "com.earendil.pi.session",
                "com.earendil.pi.tool", "com.earendil.pi.llm", "com.earendil.pi.context",
                "com.earendil.pi.security", "com.earendil.pi.agent"));
        return rules;
    }

    private static Set<String> allowed(String... packages) {
        return new HashSet<String>(Arrays.asList(packages));
    }

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(com\\.earendil\\.pi[\\w.]*);", Pattern.MULTILINE);

    @Test public void packageDependenciesPointDownward() throws Exception {
        File sourceRoot = new File("src/main/java");
        assertTrue("source root not found (run tests from the module directory): " + sourceRoot.getAbsolutePath(), sourceRoot.isDirectory());
        List<String> violations = new ArrayList<String>();
        for (File file : sources(sourceRoot, new ArrayList<File>())) {
            String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Matcher packageMatcher = PACKAGE.matcher(body);
            assertTrue("missing package declaration: " + file, packageMatcher.find());
            String ownPackage = packageMatcher.group(1);
            Set<String> allowedImports = ALLOWED.get(ownPackage);
            assertTrue("unknown package " + ownPackage + "; add it to ArchitectureTest.ALLOWED: " + file, allowedImports != null);
            Matcher importMatcher = IMPORT.matcher(body);
            while (importMatcher.find()) {
                String imported = importMatcher.group(1);
                String importedPackage = imported.substring(0, imported.lastIndexOf('.'));
                if (!importedPackage.equals(ownPackage) && !allowedImports.contains(importedPackage)) {
                    violations.add(file.getName() + ": " + ownPackage + " must not import " + importedPackage);
                }
            }
        }
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    private static List<File> sources(File dir, List<File> acc) {
        File[] children = dir.listFiles();
        if (children == null) return acc;
        for (File child : children) {
            if (child.isDirectory()) sources(child, acc);
            else if (child.getName().endsWith(".java")) acc.add(child);
        }
        return acc;
    }
}
