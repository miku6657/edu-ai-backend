import unittest
from unittest.mock import patch

from app.rag_service import (
    build_retrieval_query,
    build_system_prompt,
    rag_stream,
)


class RagMultiTurnTest(unittest.TestCase):

    def test_standalone_question(self):
        self.assertEqual(
            build_retrieval_query("什么是 Java？", []),
            "什么是 Java？",
        )

    def test_new_question_does_not_mix_old_topic(self):
        history = [
            {"role": "user", "content": "什么是 Java？"},
            {"role": "assistant", "content": "Java 是面向对象语言。"},
        ]
        question = "请解释 MySQL 索引的 B+Tree 结构和查询过程。"
        self.assertEqual(
            build_retrieval_query(question, history),
            question,
        )

    def test_self_contained_question(self):
        history = [
            {"role": "user", "content": "什么是 Java？"},
            {"role": "assistant", "content": "Java 是面向对象语言。"},
        ]
        question = "Java 有什么特点？"
        self.assertEqual(
            build_retrieval_query(question, history),
            question,
        )

    def test_follow_up_contains_context(self):
        history = [
            {"role": "user", "content": "什么是 Java？"},
            {
                "role": "assistant",
                "content": "Java 是一种面向对象的编程语言。",
            },
        ]
        query = build_retrieval_query(
            "它有什么特点？",
            history,
        )
        self.assertIn("什么是 Java？", query)
        self.assertIn("面向对象", query)
        self.assertIn("它有什么特点？", query)

    def test_short_ne_question_is_follow_up(self):
        history = [
            {"role": "user", "content": "什么是 Java？"},
            {"role": "assistant", "content": "Java 是面向对象语言。"},
        ]
        query = build_retrieval_query("跨平台呢", history)
        self.assertIn("什么是 Java？", query)
        self.assertIn("跨平台呢", query)

    def test_system_prompt_mentions_history_rule(self):
        prompt = build_system_prompt("【资料】")
        self.assertIn("历史对话只用于理解", prompt)
        self.assertIn("【本轮课程资料】", prompt)

    @patch("app.rag_service.chat_stream")
    @patch("app.rag_service.search_course_by_collection")
    def test_rag_stream_uses_retrieval_query(
        self,
        mock_search,
        mock_chat_stream,
    ):
        mock_search.return_value = [{
            "document_id": "102",
            "page": 3,
            "chunk_index": 0,
            "section_title": "1.1 Java 概述",
            "text": "Java 是一种面向对象的程序设计语言。",
        }]
        mock_chat_stream.return_value = iter([
            "Java 具有跨平台等特点。"
        ])
        history = [
            {"role": "user", "content": "什么是 Java？"},
            {
                "role": "assistant",
                "content": "Java 是一种面向对象的程序设计语言。",
            },
        ]

        list(rag_stream(
            course_id=5,
            question="它有什么特点？",
            history=history,
            allowed_document_ids=["102"],
            collection_name_value="course_5",
            embedding_model="test-embedding",
            chat_model="test-chat",
        ))

        searched_query = mock_search.call_args.kwargs["query"]
        self.assertIn("什么是 Java？", searched_query)
        self.assertIn("它有什么特点？", searched_query)

        mock_chat_stream.assert_called_once()
        messages = mock_chat_stream.call_args.args[0]
        self.assertEqual(messages[0]["role"], "system")
        self.assertIn(
            "历史对话只用于理解",
            messages[0]["content"],
        )
        self.assertEqual(
            messages[-1],
            {
                "role": "user",
                "content": "它有什么特点？",
            },
        )


if __name__ == "__main__":
    unittest.main()
