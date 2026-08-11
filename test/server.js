const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const PORT = Number(process.env.PORT || 8092);
const RAG_API_BASE_URL = process.env.RAG_API_BASE_URL || "http://127.0.0.1:8081";
const PUBLIC_DIR = path.join(__dirname, "public");

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml"
};

const server = http.createServer((request, response) => {
  if (request.url.startsWith("/api/")) {
    return proxyToRag(request, response);
  }
  return serveStatic(request, response);
});

function proxyToRag(request, response) {
  const target = new URL(request.url, RAG_API_BASE_URL);
  const proxyRequest = http.request(
    target,
    {
      method: request.method,
      headers: {
        ...request.headers,
        host: target.host
      }
    },
    (proxyResponse) => {
      response.writeHead(proxyResponse.statusCode || 502, proxyResponse.headers);
      proxyResponse.pipe(response);
    }
  );

  proxyRequest.on("error", () => {
    sendJson(response, 502, {
      error: "rag service unavailable",
      target: RAG_API_BASE_URL
    });
  });

  request.pipe(proxyRequest);
}

function serveStatic(request, response) {
  const url = new URL(request.url, `http://${request.headers.host || "localhost"}`);
  const requestPath = url.pathname === "/" ? "/index.html" : url.pathname;
  const resolvedPath = path.resolve(PUBLIC_DIR, `.${requestPath}`);

  if (!resolvedPath.startsWith(`${PUBLIC_DIR}${path.sep}`) && resolvedPath !== PUBLIC_DIR) {
    return sendText(response, 403, "Forbidden");
  }

  fs.readFile(resolvedPath, (error, content) => {
    if (error) return sendText(response, 404, "Not found");
    const extension = path.extname(resolvedPath).toLowerCase();
    response.writeHead(200, { "Content-Type": mimeTypes[extension] || "application/octet-stream" });
    response.end(content);
  });
}

function sendJson(response, statusCode, payload) {
  response.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload));
}

function sendText(response, statusCode, text) {
  response.writeHead(statusCode, { "Content-Type": "text/plain; charset=utf-8" });
  response.end(text);
}

server.listen(PORT, "127.0.0.1", () => {
  console.log(`Alicia RAG debug web running at http://127.0.0.1:${PORT}`);
  console.log(`Proxying /api/* to ${RAG_API_BASE_URL}`);
});
