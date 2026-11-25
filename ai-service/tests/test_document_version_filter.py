import unittest
from unittest.mock import patch, MagicMock

from app.rag_service import rag_stream
from app.vector_store import search_course_by_collection


class DocumentVersionFilterTest(unittest.TestCase):

    @patch("app.vector_store.client")
    def test_empty_allowed_ids(self, mock_client):
        result = search_course_by_collection(
            collection_name_value="course_1",
            query="Java集合框架",
            allowed_document_ids=[],
        )
        self.assertEqual(result, [])
        # 空白名单不应访问ChromaDB
        mock_client.get_collection.assert_not_called()

    @patch("app.vector_store.embed_query")
    @patch("app.vector_store.create_embedding_client")
    @patch("app.vector_store.client")
    def test_only_current_document_is_returned(
        self,
        mock_client,
        mock_embedding_client,
        mock_embed_query,
    ):
        collection = MagicMock()
        collection.count.return_value = 10
        mock_client.get_collection.return_value = collection

        mock_embed_query.return_value = [0.1, 0.2, 0.3]

        collection.query.return_value = {
            "documents": [["片段A"]],
            "metadatas": [[
                {"document_id": "102", "page": 1, "chunk_index": 0}
            ]],
            "distances": [[0.2]],
        }

        result = search_course_by_collection(
            collection_name_value="course_1",
            query="Java集合框架",
            allowed_document_ids=["102", "105"],
            top_k=5,
        )

        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["document_id"], "102")

        # 检索时必须同时限定 active 与 document_id 白名单
        call_kwargs = collection.query.call_args.kwargs
        self.assertEqual(
            call_kwargs["where"],
            {
                "$and": [
                    {"active": "1"},
                    {"document_id": {"$in": ["102", "105"]}},
                ]
            },
        )

    @patch("app.rag_service.chat_stream")
    @patch("app.rag_service.search_course_by_collection")
    def test_empty_course_does_not_call_models(
        self,
        mock_search,
        mock_chat_stream,
    ):
        events = list(rag_stream(
            course_id=5,
            question="问题",
            history=[],
            allowed_document_ids=[],
            collection_name_value="course_5",
        ))

        mock_search.assert_not_called()
        mock_chat_stream.assert_not_called()
        # 仍然返回 done 结束事件
        self.assertTrue(any("event: done" in e for e in events))

    @patch("app.rag_service.chat_stream")
    @patch("app.rag_service.search_course_by_collection")
    def test_no_hits_does_not_call_chat(
        self,
        mock_search,
        mock_chat_stream,
    ):
        mock_search.return_value = []

        events = list(rag_stream(
            course_id=5,
            question="问题",
            history=[],
            allowed_document_ids=["102"],
            collection_name_value="course_5",
        ))

        mock_chat_stream.assert_not_called()
        self.assertTrue(any("event: done" in e for e in events))


if __name__ == "__main__":
    unittest.main()
