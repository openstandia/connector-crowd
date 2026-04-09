package config

import (
	"fmt"
	"os"
)

type Config struct {
	Port         string
	ContextPath  string
	DBHost       string
	DBPort       string
	DBName       string
	DBUser       string
	DBPassword   string
	AuthUser     string
	AuthPassword string
}

func Load() *Config {
	return &Config{
		Port:         getEnv("CROWD_MOCK_PORT", "8095"),
		ContextPath:  getEnv("CROWD_MOCK_CONTEXT_PATH", "/crowd"),
		DBHost:       getEnv("CROWD_MOCK_DB_HOST", "localhost"),
		DBPort:       getEnv("CROWD_MOCK_DB_PORT", "5432"),
		DBName:       getEnv("CROWD_MOCK_DB_NAME", "crowd_mock"),
		DBUser:       getEnv("CROWD_MOCK_DB_USER", "postgres"),
		DBPassword:   getEnv("CROWD_MOCK_DB_PASSWORD", "postgres"),
		AuthUser:     getEnv("CROWD_MOCK_AUTH_USER", "midpoint-connector"),
		AuthPassword: getEnv("CROWD_MOCK_AUTH_PASSWORD", "password"),
	}
}

func (c *Config) DBURL() string {
	return fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable",
		c.DBUser, c.DBPassword, c.DBHost, c.DBPort, c.DBName)
}

func getEnv(key, defaultValue string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultValue
}
