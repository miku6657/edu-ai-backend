# -EDU-05 V1.0 开发基线

三个角色： 课程负责人、学生、系统管理员。

技术架构： Vue 3 + Spring Boot + FastAPI + MySQL + ChromaDB。

AI 能力： 云端对话 API + 云端 Embedding API + RAG + MySQL 多轮记忆。

知识库模式： 课程负责人上传并发布 PDF，学生共享课程知识库，各自保存个人聊天记录。

部署方案： 开发时本地调试代码、Docker 运行数据库；交付时使用 Docker Compose 单机部署。

核心目标： 实现一个具有真实知识检索、引用溯源、连续对话和基础学习辅助能力的智能学习助手。
