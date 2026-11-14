
FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/requirements.txt

RUN pip install --no-cache-dir \
    -r /app/requirements.txt

COPY app/ /app/app/

RUN mkdir -p /data/pdfs /app/data/chroma

EXPOSE 8001

CMD ["python", "-m", "uvicorn", \
     "app.main:app", \
     "--host", "0.0.0.0", \
     "--port", "8001", \
     "--workers", "1"]
