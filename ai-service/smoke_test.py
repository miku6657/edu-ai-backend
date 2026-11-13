from app.chat_service import chat
from app.embedding_service import embed_query


def test_embedding():
    print("测试 SiliconFlow Embedding...")

    vector = embed_query(
        "Java 是一门面向对象编程语言"
    )

    print("Embedding 调用成功")
    print("向量维度：", len(vector))


def test_deepseek():
    print("\n测试 DeepSeek Chat...")

    answer = chat([
        {
            "role": "user",
            "content": "请用一句话介绍 Java 编程语言"
        }
    ])

    print("DeepSeek 调用成功")
    print("回答：", answer)


if __name__ == "__main__":
    test_embedding()
    test_deepseek()

    print("\n两个 AI 服务调用完成")
