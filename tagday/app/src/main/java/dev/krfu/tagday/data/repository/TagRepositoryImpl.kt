package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.TagDao
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
) : TagRepository {
    override fun observeAll(): Flow<List<Tag>> = tagDao.observeAll()

    override fun observeFiltered(query: String): Flow<List<Tag>> = tagDao.observeFiltered(query)

    override suspend fun createTag(name: String, color: Int, type: TagType): Long =
        tagDao.insert(Tag(name = name, type = type, color = color, createdAt = System.currentTimeMillis()))
}
