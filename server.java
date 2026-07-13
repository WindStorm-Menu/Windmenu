// Importação das bibliotecas necessárias para servidor HTTP, JSON e cliente HTTP
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

// Classe principal do servidor Java que se integra ao projeto HTML
public class Server {
    // Configuração do Supabase – substitua pelos seus dados
    private static final String SUPABASE_URL = "https://wdjfwtckllamgjmqiurf.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndkamZ3dGNrbGxhbWdqbXFpdXJmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM3MjIxNDEsImV4cCI6MjA5OTI5ODE0MX0.nPLczZoRmMS4yMobICPNqelwsXUJTTuFs70IiLJ7pw8";
    private static final String SUPABASE_SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndkamZ3dGNrbGxhbWdqbXFpdXJmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM3MjIxNDEsImV4cCI6MjA5OTI5ODE0MX0.nPLczZoRmMS4yMobICPNqelwsXUJTTuFs70IiLJ7pw8"; // para operações administrativas

    // Cliente HTTP para comunicação com a API REST do Supabase
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) throws IOException {
        // Cria o servidor HTTP na porta 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        // Define um pool de threads para lidar com múltiplas requisições
        server.setExecutor(Executors.newFixedThreadPool(10));

        // Registra os manipuladores de rotas (endpoints)
        server.createContext("/", new StaticFileHandler());   // serve index.html e arquivos estáticos
        server.createContext("/api/signup", new SignupHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/codes", new CodesHandler());
        server.createContext("/api/tickets", new TicketsHandler());
        server.createContext("/api/users", new UsersHandler());

        server.start();
        System.out.println("Servidor Java rodando em http://localhost:8080");
    }

    // Manipulador que serve arquivos estáticos (devolve o index.html)
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Habilita CORS para o frontend
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            // Lê o arquivo index.html da pasta resources (coloque-o lá)
            InputStream is = getClass().getClassLoader().getResourceAsStream("index.html");
            if (is == null) {
                String response = "index.html não encontrado";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }
            byte[] fileBytes = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }
    }

    // Manipulador para registro de novo usuário
    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // Lê o corpo da requisição em JSON
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseJson(body);
            String username = data.get("username");
            String email = data.get("email");
            String password = data.get("password");

            // Verifica se o usuário já existe na tabela "users" do Supabase
            String checkUrl = SUPABASE_URL + "/rest/v1/users?email=eq." + email;
            HttpRequest checkReq = HttpRequest.newBuilder()
                    .uri(URI.create(checkUrl))
                    .header("apikey", SUPABASE_SERVICE_ROLE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                    .GET()
                    .build();
            try {
                HttpResponse<String> checkResp = httpClient.send(checkReq, HttpResponse.BodyHandlers.ofString());
                if (!checkResp.body().equals("[]")) {
                    sendJsonResponse(exchange, 409, "{\"error\":\"Usuário já existe\"}");
                    return;
                }
            } catch (InterruptedException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Erro ao verificar usuário\"}");
                return;
            }

            // Insere o novo usuário na tabela "users" do Supabase
            String insertBody = String.format("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"role\":\"free\"}", username, email, password);
            HttpRequest insertReq = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/rest/v1/users"))
                    .header("apikey", SUPABASE_SERVICE_ROLE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(insertBody))
                    .build();
            try {
                HttpResponse<String> insertResp = httpClient.send(insertReq, HttpResponse.BodyHandlers.ofString());
                if (insertResp.statusCode() == 201) {
                    sendJsonResponse(exchange, 201, insertResp.body());
                } else {
                    sendJsonResponse(exchange, insertResp.statusCode(), "{\"error\":\"Falha ao criar usuário\"}");
                }
            } catch (InterruptedException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Erro interno do servidor\"}");
            }
        }
    }

    // Manipulador de login
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseJson(body);
            String identity = data.get("identity");
            String password = data.get("password");

            // Busca o usuário por email ou nome de usuário
            String url = SUPABASE_URL + "/rest/v1/users?or=(email.eq." + identity + ",username.eq." + identity + ")";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                    .GET()
                    .build();
            try {
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.body().equals("[]")) {
                    sendJsonResponse(exchange, 401, "{\"error\":\"Credenciais inválidas\"}");
                    return;
                }
                // Validação simples da senha (em produção utilize hash)
                String json = resp.body();
                if (json.contains("\"password\":\"" + password + "\"")) {
                    sendJsonResponse(exchange, 200, resp.body());
                } else {
                    sendJsonResponse(exchange, 401, "{\"error\":\"Senha incorreta\"}");
                }
            } catch (InterruptedException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Erro interno\"}");
            }
        }
    }

    // Manipulador de códigos (scripts) – obtém, insere, deleta
    static class CodesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            String method = exchange.getRequestMethod();
            try {
                if ("GET".equals(method)) {
                    // Obtém códigos visíveis para a role informada (parâmetro ?role=...)
                    String query = exchange.getRequestURI().getQuery();
                    String role = "free";
                    if (query != null && query.contains("role=")) {
                        role = query.split("role=")[1].split("&")[0];
                    }
                    String url = SUPABASE_URL + "/rest/v1/codes?role=eq." + role;
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("apikey", SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    sendJsonResponse(exchange, resp.statusCode(), resp.body());
                } else if ("POST".equals(method)) {
                    // Adiciona um novo código (requer autorização de staff, aqui simplificada)
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(SUPABASE_URL + "/rest/v1/codes"))
                            .header("apikey", SUPABASE_SERVICE_ROLE_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=representation")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    sendJsonResponse(exchange, resp.statusCode(), resp.body());
                } else if ("DELETE".equals(method)) {
                    // Exclui um código pelo id (passado como ?id=...)
                    String query = exchange.getRequestURI().getQuery();
                    if (query == null || !query.contains("id=")) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Parâmetro id necessário\"}");
                        return;
                    }
                    String id = query.split("id=")[1].split("&")[0];
                    String url = SUPABASE_URL + "/rest/v1/codes?id=eq." + id;
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("apikey", SUPABASE_SERVICE_ROLE_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                            .DELETE()
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    sendJsonResponse(exchange, resp.statusCode(), resp.body());
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (InterruptedException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Erro interno\"}");
            }
        }
    }

    // Manipulador de tickets de suporte
    static class TicketsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            String method = exchange.getRequestMethod();
            try {
                if ("GET".equals(method)) {
                    // Lista todos os tickets (staff) ou um ticket por email (?email=...)
                    String query = exchange.getRequestURI().getQuery();
                    String url = SUPABASE_URL + "/rest/v1/tickets";
                    if (query != null && query.contains("email=")) {
                        String email = query.split("email=")[1].split("&")[0];
                        url += "?email=eq." + email;
                    }
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("apikey", SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    sendJsonResponse(exchange, resp.statusCode(), resp.body());
                } else if ("POST".equals(method)) {
                    // Cria ou atualiza um ticket (corpo JSON)
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(SUPABASE_URL + "/rest/v1/tickets"))
                            .header("apikey", SUPABASE_SERVICE_ROLE_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=representation")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    sendJsonResponse(exchange, resp.statusCode(), resp.body());
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (InterruptedException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Erro interno\"}");
            }
        }
    }

    // Manipulador para gerenciamento de usuários (listagem, atualização de cargo)
    static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            String method = exchange.getRequestMethod();
            try {
                if ("GET".equals(method)) {
                    String url = SUPABASE_URL + "/rest/v1/users";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("apikey", SUPABASE_SERVICE_ROLE_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    sendJsonResponse(exchange, resp.statusCode(), resp.body());
                } else if ("PATCH".equals(method)) {
                    // Atualiza cargo de um usuário (?email=...)
                    String query = exchange.getRequestURI().getQuery();
                    if (query == null || !query.contains("email=")) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Email necessário\"}");
                        return;
                    }
                    String email = query.split("email=")[1].split("&")[0];
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String url = SUPABASE_URL + "/rest/v1/users?email=eq." + email;
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("apikey", SUPABASE_SERVICE_ROLE_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=representation")
                            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    sendJsonResponse(exchange, resp.statusCode(), resp.body());
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (InterruptedException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Erro interno\"}");
            }
        }
    }

    // Método utilitário para enviar resposta JSON
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // Conversor simples de JSON para Map (apenas para campos simples)
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        // Remove chaves e espaços
        json = json.trim().replaceAll("[{}\"]", "");
        if (json.isEmpty()) return map;
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }
}
