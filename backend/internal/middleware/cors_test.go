package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

const testAllowedOrigin = "http://localhost:5173"

func TestCORSAllowsConfiguredPreflight(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(CORS([]string{testAllowedOrigin}))
	router.POST("/assets", func(ctx *gin.Context) { ctx.Status(http.StatusOK) })

	request := httptest.NewRequest(http.MethodOptions, "/assets", nil)
	request.Header.Set("Origin", testAllowedOrigin)
	request.Header.Set("Access-Control-Request-Method", http.MethodPost)
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)

	if response.Code != http.StatusNoContent {
		t.Fatalf("expected status 204, got %d", response.Code)
	}
	assertCORSHeaders(t, response.Header())
}

func TestCORSAddsHeadersToConfiguredOrigin(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(CORS([]string{testAllowedOrigin}))
	router.GET("/health", func(ctx *gin.Context) { ctx.Status(http.StatusOK) })

	request := httptest.NewRequest(http.MethodGet, "/health", nil)
	request.Header.Set("Origin", testAllowedOrigin)
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)

	assertCORSHeaders(t, response.Header())
}

func TestCORSRejectsUnconfiguredOrigin(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(CORS([]string{testAllowedOrigin}))
	router.GET("/health", func(ctx *gin.Context) { ctx.Status(http.StatusOK) })

	request := httptest.NewRequest(http.MethodGet, "/health", nil)
	request.Header.Set("Origin", "https://untrusted.example.com")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)

	if origin := response.Header().Get("Access-Control-Allow-Origin"); origin != "" {
		t.Fatalf("expected no CORS origin header, got %q", origin)
	}
}

func assertCORSHeaders(t *testing.T, headers http.Header) {
	t.Helper()
	if got := headers.Get("Access-Control-Allow-Origin"); got != testAllowedOrigin {
		t.Fatalf("unexpected allowed origin %q", got)
	}
	if got := headers.Get("Access-Control-Allow-Methods"); got != corsAllowedMethods {
		t.Fatalf("unexpected allowed methods %q", got)
	}
	if got := headers.Get("Access-Control-Allow-Headers"); got != corsAllowedHeaders {
		t.Fatalf("unexpected allowed headers %q", got)
	}
	if got := headers.Get("Access-Control-Allow-Credentials"); got != "" {
		t.Fatalf("credentials must not be enabled, got %q", got)
	}
}
