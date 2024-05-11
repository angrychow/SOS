package sos.http;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import sos.kernel.Main;
import sos.kernel.device.DeviceStatus;
import sos.kernel.device.HttpDevice1;
import sos.kernel.filesystem.FileTree;
import sos.kernel.models.FileTreeNode;
import sos.kernel.models.MMUInfo;
import sos.kernel.models.PCB;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static sos.kernel.Main.*;

public class Controller {
    private static byte[] getResourceFileContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllBytes(path);
    }
    public static void main(String[] args) throws Exception {
        Bootstrap();
        HttpDevice1 httpDevice1 = new HttpDevice1();
        httpDevice1.DeviceName = "httpdevice1";
        httpDevice1.LoadDriver();
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
        server.createContext("/api/link",new LinkFileHandle());
        server.createContext("/api/mmu_info", new MMUInfoHandle());
        server.createContext("/api/device_table", new DevicesHandle());
        server.createContext("/api/http_input", new HttpInputHandle());
        // 设置根URL的处理程序
        server.createContext("/", exchange -> {
            String filePath = "index.html";
            InputStream inputStream = Controller.class.getClassLoader().getResourceAsStream(filePath);
            byte[] response = inputStream.readAllBytes();
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        });

        // 设置/static/* URL的处理程序
        server.createContext("/static/", exchange -> {
            String filePath = exchange.getRequestURI().getPath().substring(1);
            InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(filePath);
            byte[] response = inputStream.readAllBytes();
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        });

        server.setExecutor(Executors.newFixedThreadPool(1));
        server.start();
    }

    static class LinkFileHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var str = exchange.getRequestBody();
            Map<String, Object> body = JSON.parseObject(str);
            var src = (String) body.get("src");
            var dst = (String) body.get("dst");//将dst文件变为链接文件,链接到src文件
            var ok = false;
            try {
                ok = FS.SymbolicLink(src, dst);
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
    static class DevicesHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                var devices = DeviceTable;
                Map<String, DeviceStatus> deviceStatusMap = devices.stream()
                        .collect(Collectors.toMap(i->i.DeviceName, i->i.Status));
                var str = JSON.toJSONString(deviceStatusMap);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, str.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(str.getBytes());
                outputStream.close();
            } catch (Exception e) {
                System.out.println(e);
                throw e;
            }

        }
    }


    static class MMUInfoHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int[] pageBitmap;
            try {
                pageBitmap = GetPhysicalMemory();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Map<String,Integer> indexValueMap= IntStream.range(0,pageBitmap.length)
                    .filter(i->pageBitmap[i]!=0)
                    .boxed()
                    .collect(Collectors.toMap(String::valueOf, i->pageBitmap[i]));
            MMUInfo mmuInfo = new MMUInfo();
            mmuInfo.pageBitmap= indexValueMap;
            mmuInfo.memoryUsage= controller.MemoryUsage();
            String response =JSON.toJSONString(mmuInfo);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }
    static class TickHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var str = "";
            try {
                str = NextTick();
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
            var priority = (String) body.get("priority");
            System.out.println(script);
            PCB p;
            try {
                p = CreateProcess((String) script, (String) pname);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            switch (priority) {
                case "HIGH" -> p.Priority = PCB.HIGH;
                case "LOW" -> p.Priority = PCB.LOW;
                default -> p.Priority = PCB.MEDIUM;
            }
            String response = p != null ? "OK" :"Not OK";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }
    static class HttpInputHandle implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var str = exchange.getRequestBody();
            Map<String, Object> body = JSON.parseObject(str);
            var deviceName = (String) body.get("deviceName");
            var content = (String) body.get("content");
            var ok = false;
            try {
                ok = HttpInput(deviceName, content);
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
            var obj = GetSOSInfo();
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
            var response = CreateFile(filename, filetype, deviceName, content, path) ? "OK" : "Not OK";
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
                ok = DeleteFile(filepath);
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
                obj = FoundFile(filepath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            System.out.println(obj);
            var str_ = JSON.toJSONString(obj);
//            System.out.printf("[Found File] %s \n", str_);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, str_.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(str_.getBytes());
            outputStream.close();
        }
    }
}
