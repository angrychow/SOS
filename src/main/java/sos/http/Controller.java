package sos.http;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import sos.kernel.models.FileTreeNode;
import sos.kernel.models.PCB;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;

public class Controller {
    public static void main(String[] args) throws Exception {
        sos.kernel.Main.Bootstrap();
        var server = HttpServer.create(
                new InetSocketAddress(8080),
                0
        );
        server.createContext("/api/tick", new TickHandle());
        server.createContext("/api/submit", new SubmitHandle());
        server.createContext("/api/info", new InfoHandle());
        server.createContext("/api/find", new FindFileHandle());
        server.createContext("/api/create", new CreateFileHandle());
        server.createContext("/api/delete", new DeleteFileHandle());
        server.setExecutor(null);
        server.start();
    }
    static class TickHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var str = "";
            try {
                str = sos.kernel.Main.NextTick();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String response = str;

            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());

            outputStream.close();
        }
    }

    static class SubmitHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var str = exchange.getRequestBody();
            Map<String, Object> body = JSON.parseObject(str);
            var script = body.get("script");
            var pname = body.get("name");
            System.out.println(script);
            var ok = false;
            try {
                ok = sos.kernel.Main.CreateProcess((String) script, (String) pname);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String response = ok ? "OK" :"Not OK";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    static class InfoHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var obj = sos.kernel.Main.GetSOSInfo();
            var str = JSON.toJSONString(obj);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, str.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(str.getBytes());
            outputStream.close();
        }
    }

    static class CreateFileHandle implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            var str = exchange.getRequestBody();
            Map<String, Object> body = JSON.parseObject(str);
            var filename = (String) body.get("filename");
            var filetype = (String) body.get("filetype");
            var deviceName = (String) body.get("deviceName");
            var content = (String) body.get("content");
            var path = (String) body.get("path");
            var response = sos.kernel.Main.CreateFile(filename, filetype, deviceName, content, path) ? "OK" : "Not OK";
            if(response.equals("OK"))exchange.sendResponseHeaders(200, response.getBytes().length);
            else exchange.sendResponseHeaders(400, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    static class DeleteFileHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var str = exchange.getRequestBody();
            Map<String, Object> body = JSON.parseObject(str);
            var filepath = (String) body.get("path");
            var ok = false;
            try {
                ok = sos.kernel.Main.DeleteFile(filepath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String response = ok ? "OK" :"Not OK";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    static class FindFileHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var str = exchange.getRequestBody();
            Map<String, Object> body = JSON.parseObject(str);
            var filepath = (String) body.get("path");
            FileTreeNode obj;
            try {
                obj = sos.kernel.Main.FoundFile(filepath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            var str_ = JSON.toJSONString(obj);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, str_.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(str_.getBytes());
            outputStream.close();
        }
    }
}
