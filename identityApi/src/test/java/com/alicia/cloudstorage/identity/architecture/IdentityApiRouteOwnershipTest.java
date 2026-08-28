package com.alicia.cloudstorage.identity.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApiRouteOwnershipTest {

    private static final Path CONTROLLER_SOURCE_ROOT = Path.of(
            "src", "main", "java", "com", "alicia", "cloudstorage", "identity", "controller"
    );
    private static final Path SERVICE_SOURCE_ROOT = Path.of(
            "src", "main", "java", "com", "alicia", "cloudstorage", "identity", "service"
    );
    private static final List<String> ALLOWED_API_PREFIXES = List.of("/api/identity");
    private static final List<String> FORBIDDEN_CLOUD_PREFIXES = List.of(
            "/api/health",
            "/api/cloud-profile",
            "/api/storage",
            "/api/share-links",
            "/api/public/share-links",
            "/api/admin/cloud-users",
            "/api/admin/cloud-operations",
            "/api/admin/app-package",
            "/api/app-package",
            "/api/auth",
            "/api/admin/users"
    );
    private static final Pattern MAPPING_ANNOTATION = Pattern.compile(
            "@(?:RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\s*\\(([^)]*)\\)",
            Pattern.DOTALL
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern ENDPOINT_MAPPING = Pattern.compile(
            "@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\s*(?:\\([^)]*\\))?",
            Pattern.DOTALL
    );
    private static final Pattern SERVICE_FIELD = Pattern.compile(
            "private\\s+final\\s+([A-Z][A-Za-z0-9]*Service)\\s+([a-z][A-Za-z0-9]*Service)\\s*;"
    );
    private static final Pattern SERVICE_CALL = Pattern.compile("\\b([a-z][A-Za-z0-9]*Service)\\.([a-z][A-Za-z0-9]*)\\s*\\(");

    @Test
    void identityControllersOnlyExposeIdentityOwnedApiRoutes() {
        Path controllerRoot = controllerSourceRoot();

        List<String> violations = regularJavaFiles(controllerRoot)
                .stream()
                .flatMap(path -> apiRouteMappings(controllerRoot, path).stream())
                .filter(route -> !matchesAnyPrefix(route.value(), ALLOWED_API_PREFIXES))
                .map(route -> route.location() + " exposes " + route.value())
                .toList();

        assertThat(violations)
                .as("identityApi controllers must keep API routes under /api/identity/**.")
                .isEmpty();
    }

    @Test
    void identityControllersDoNotExposeCloudApiRoutes() {
        Path controllerRoot = controllerSourceRoot();

        List<String> violations = regularJavaFiles(controllerRoot)
                .stream()
                .flatMap(path -> apiRouteMappings(controllerRoot, path).stream())
                .filter(route -> matchesAnyPrefix(route.value(), FORBIDDEN_CLOUD_PREFIXES))
                .map(route -> route.location() + " claims cloud-owned route " + route.value())
                .toList();

        assertThat(violations)
                .as("Cloud-owned and legacy identity routes must stay out of identityApi controllers.")
                .isEmpty();
    }

    @Test
    void identityAdminControllerEntrypointsCarryAuthorizationHeader() {
        Path controllerRoot = controllerSourceRoot();

        List<String> violations = adminControllerSources(controllerRoot)
                .stream()
                .flatMap(path -> endpointMappingsMissingAuthorizationHeader(controllerRoot, path).stream())
                .toList();

        assertThat(violations)
                .as("Every /api/identity/admin/** controller endpoint must pass the Authorization header to its admin service.")
                .isEmpty();
    }

    @Test
    void identityAdminControllerServicesRequireAdminUser() {
        Path controllerRoot = controllerSourceRoot();
        Path serviceRoot = serviceSourceRoot();

        List<String> violations = adminControllerSources(controllerRoot)
                .stream()
                .flatMap(path -> adminServiceCallsMissingAdminGuard(controllerRoot, serviceRoot, path).stream())
                .toList();

        assertThat(violations)
                .as("Services reached from /api/identity/admin/** controllers must guard their called methods with requireAdminUser.")
                .isEmpty();
    }

    private static Path controllerSourceRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path moduleRelative = current.resolve(CONTROLLER_SOURCE_ROOT);
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }

        Path repositoryRelative = current.resolve("identityApi").resolve(CONTROLLER_SOURCE_ROOT);
        if (Files.exists(repositoryRelative)) {
            return repositoryRelative;
        }

        throw new IllegalStateException("Unable to locate identityApi controller source root from " + current);
    }

    private static Path serviceSourceRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path moduleRelative = current.resolve(SERVICE_SOURCE_ROOT);
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }

        Path repositoryRelative = current.resolve("identityApi").resolve(SERVICE_SOURCE_ROOT);
        if (Files.exists(repositoryRelative)) {
            return repositoryRelative;
        }

        throw new IllegalStateException("Unable to locate identityApi service source root from " + current);
    }

    private static List<Path> regularJavaFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan " + root, ex);
        }
    }

    private static List<ApiRouteMapping> apiRouteMappings(Path controllerRoot, Path path) {
        String text = readText(path);
        String relativePath = controllerRoot.relativize(path).toString().replace('\\', '/');

        return MAPPING_ANNOTATION.matcher(text)
                .results()
                .flatMap(annotation -> routeLiterals(annotation.group(1)).stream()
                        .filter(IdentityApiRouteOwnershipTest::isApiRouteLiteral)
                        .map(IdentityApiRouteOwnershipTest::normalizeApiRoute)
                        .map(route -> new ApiRouteMapping(relativePath, lineNumber(text, annotation.start()), route)))
                .toList();
    }

    private static List<Path> adminControllerSources(Path controllerRoot) {
        return regularJavaFiles(controllerRoot).stream()
                .filter(path -> readText(path).contains("/api/identity/admin"))
                .toList();
    }

    private static List<String> endpointMappingsMissingAuthorizationHeader(Path controllerRoot, Path path) {
        String text = readText(path);
        String relativePath = controllerRoot.relativize(path).toString().replace('\\', '/');
        List<String> violations = new ArrayList<>();
        Matcher matcher = ENDPOINT_MAPPING.matcher(text);

        while (matcher.find()) {
            int methodStart = text.indexOf("public ", matcher.end());
            if (methodStart < 0) {
                violations.add(relativePath + ":" + lineNumber(text, matcher.start()) + " has no public handler method");
                continue;
            }

            int methodBodyStart = text.indexOf('{', methodStart);
            if (methodBodyStart < 0) {
                violations.add(relativePath + ":" + lineNumber(text, matcher.start()) + " has no handler method body");
                continue;
            }

            String methodSignature = text.substring(methodStart, methodBodyStart);
            if (!methodSignature.contains("HttpHeaders.AUTHORIZATION")) {
                violations.add(relativePath + ":" + lineNumber(text, matcher.start()) + " does not receive Authorization");
            }
        }

        return violations;
    }

    private static List<String> adminServiceCallsMissingAdminGuard(Path controllerRoot, Path serviceRoot, Path controllerPath) {
        String controllerSource = readText(controllerPath);
        String relativePath = controllerRoot.relativize(controllerPath).toString().replace('\\', '/');
        Map<String, String> serviceFields = SERVICE_FIELD.matcher(controllerSource)
                .results()
                .collect(java.util.stream.Collectors.toMap(match -> match.group(2), match -> match.group(1)));
        List<String> violations = new ArrayList<>();
        Matcher callMatcher = SERVICE_CALL.matcher(controllerSource);

        while (callMatcher.find()) {
            String fieldName = callMatcher.group(1);
            String serviceClass = serviceFields.get(fieldName);
            if (serviceClass == null) {
                continue;
            }

            String methodName = callMatcher.group(2);
            Path servicePath = serviceRoot.resolve(serviceClass + ".java");
            if (!Files.exists(servicePath)) {
                violations.add(relativePath + ":" + lineNumber(controllerSource, callMatcher.start())
                        + " calls missing service " + serviceClass + "." + methodName);
                continue;
            }

            String serviceSource = readText(servicePath);
            if (!publicMethodCallsRequireAdminUser(serviceSource, methodName)) {
                violations.add(relativePath + ":" + lineNumber(controllerSource, callMatcher.start())
                        + " calls " + serviceClass + "." + methodName
                        + " without a direct requireAdminUser guard");
            }
        }

        return violations;
    }

    private static boolean publicMethodCallsRequireAdminUser(String serviceSource, String methodName) {
        Pattern methodDeclaration = Pattern.compile("\\bpublic\\s+[\\s\\S]*?\\b" + Pattern.quote(methodName) + "\\s*\\(");
        Matcher matcher = methodDeclaration.matcher(serviceSource);

        while (matcher.find()) {
            int parameterStart = serviceSource.indexOf('(', matcher.end() - 1);
            int parameterEnd = findMatching(serviceSource, parameterStart, '(', ')');
            if (parameterEnd < 0) {
                continue;
            }

            int bodyStart = serviceSource.indexOf('{', parameterEnd);
            if (bodyStart < 0) {
                continue;
            }

            int bodyEnd = findMatching(serviceSource, bodyStart, '{', '}');
            if (bodyEnd < 0) {
                continue;
            }

            String methodBody = serviceSource.substring(bodyStart, bodyEnd + 1);
            if (methodBody.contains("identityPrincipalService.requireAdminUser(")) {
                return true;
            }
        }

        return false;
    }

    private static List<String> routeLiterals(String annotationArguments) {
        Matcher matcher = STRING_LITERAL.matcher(annotationArguments);
        return matcher.results()
                .map(match -> match.group(1))
                .toList();
    }

    private static boolean isApiRouteLiteral(String value) {
        return value.equals("api")
                || value.equals("/api")
                || value.startsWith("api/")
                || value.startsWith("/api/");
    }

    private static String normalizeApiRoute(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    private static boolean matchesAnyPrefix(String route, List<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> route.equals(prefix) || route.startsWith(prefix + "/"));
    }

    private static int lineNumber(String text, int index) {
        return 1 + (int) text.substring(0, index)
                .chars()
                .filter(character -> character == '\n')
                .count();
    }

    private static int findMatching(String text, int openIndex, char openCharacter, char closeCharacter) {
        if (openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != openCharacter) {
            return -1;
        }

        int depth = 0;
        for (int index = openIndex; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == openCharacter) {
                depth++;
            } else if (character == closeCharacter) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private record ApiRouteMapping(String sourcePath, int lineNumber, String value) {

        String location() {
            return sourcePath + ":" + lineNumber;
        }
    }
}
