import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent

# 加载项目根目录的 .env
load_dotenv(BASE_DIR / ".env")


def resolve_path(value: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = BASE_DIR / path
    return path.resolve()


# 内部认证密钥（Java 与 Python 之间）
AI_INTERNAL_KEY = os.environ["AI_INTERNAL_KEY"]

# PDF 存储目录（指向 Java 后端的上传目录）
PDF_STORAGE_DIR = resolve_path(
    os.getenv("PDF_STORAGE_DIR", "../uploads/pdfs")
)

# ChromaDB 持久化目录
CHROMA_DIR = resolve_path(
    os.getenv("CHROMA_DIR", "./data/chroma")
)

# DeepSeek 聊天模型
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv(
    "DEEPSEEK_BASE_URL", "https://api.deepseek.com"
)
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-flash")

# SiliconFlow Embedding
EMBEDDING_API_KEY = os.getenv("EMBEDDING_API_KEY", "")
EMBEDDING_BASE_URL = os.getenv(
    "EMBEDDING_BASE_URL", "https://api.siliconflow.cn/v1"
)
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-m3")

# PDF 解析限制
MAX_PAGES = int(os.getenv("MAX_PAGES", "200"))
MAX_EXTRACTED_CHARS = int(os.getenv("MAX_EXTRACTED_CHARS", "1000000"))
