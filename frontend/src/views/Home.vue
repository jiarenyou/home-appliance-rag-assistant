<template>
  <div class="flex h-full">
    <!-- 侧边栏 - 文档管理 -->
    <aside class="w-80 bg-gray-50 border-r border-gray-200 flex flex-col">
      <div class="p-4 border-b border-gray-200">
        <h1 class="text-lg font-semibold">家电维修知识助手</h1>
      </div>

      <div class="p-4 flex-1 overflow-y-auto">
        <div class="mb-4">
          <a-upload
            :before-upload="handleBeforeUpload"
            :show-upload-list="false"
            accept=".pdf,.md,.markdown"
          >
            <a-button type="primary" block>
              <UploadOutlined />
              上传文档
            </a-button>
          </a-upload>
        </div>

        <a-divider class="my-4">文档列表</a-divider>

        <a-list
          :data-source="documents"
          :loading="loading"
          item-layout="vertical"
          size="small"
        >
          <template #renderItem="{ item }">
            <a-list-item>
              <template #actions>
                <a @click.stop="handleDelete(item.id)">
                  <DeleteOutlined class="text-red-500" />
                </a>
              </template>
              <a-list-item-meta>
                <template #title>
                  <span class="text-sm">{{ item.filename }}</span>
                </template>
                <template #description>
                  <a-tag :color="getStatusColor(item.status)">
                    {{ getStatusText(item.status) }}
                  </a-tag>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </aside>

    <!-- 主聊天区域 -->
    <main class="flex-1 flex flex-col">
      <div class="flex-1 overflow-y-auto p-6" ref="messagesContainer">
        <div v-if="messages.length === 0" class="text-center text-gray-400 mt-20">
          <CustomerServiceOutlined class="text-6xl mb-4" />
          <p class="text-lg">上传维修手册，开始智能问答</p>
        </div>

        <div v-else class="max-w-4xl mx-auto space-y-6">
          <div
            v-for="message in messages"
            :key="message.id"
            :class="[
              'flex',
              message.role === 'USER' ? 'justify-end' : 'justify-start'
            ]"
          >
            <div
              :class="[
                'max-w-2xl rounded-lg px-4 py-3',
                message.role === 'USER'
                  ? 'bg-blue-500 text-white'
                  : 'bg-gray-100 text-gray-900'
              ]"
            >
              <div v-html="renderMarkdown(message.content)"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="border-t border-gray-200 p-4">
        <div class="max-w-4xl mx-auto">
          <a-input-search
            v-model:value="inputMessage"
            placeholder="描述问题，例如：洗衣机显示 E3 错误代码怎么办？"
            size="large"
            :loading="isStreaming"
            @search="handleSend"
          >
            <template #suffix>
              <a-button
                type="primary"
                :disabled="!inputMessage.trim() || isStreaming"
                @click="handleSend"
              >
                <SendOutlined v-if="!isStreaming" />
                <LoadingOutlined v-else />
              </a-button>
            </template>
          </a-input-search>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  UploadOutlined,
  DeleteOutlined,
  CustomerServiceOutlined,
  SendOutlined,
  LoadingOutlined
} from '@ant-design/icons-vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()

// 状态
const documents = ref([])
const messages = ref([])
const inputMessage = ref('')
const isStreaming = ref(false)
const loading = ref(false)
const messagesContainer = ref(null)

// 加载文档列表
const loadDocuments = async () => {
  loading.value = true
  try {
    const response = await fetch('/api/documents')
    const result = await response.json()
    if (result.code === 200) {
      documents.value = result.data
    }
  } catch (error) {
    message.error('加载文档失败')
  } finally {
    loading.value = false
  }
}

// 上传文档
const handleBeforeUpload = async (file) => {
  const formData = new FormData()
  formData.append('file', file)

  try {
    message.loading('文档上传中，请稍候...', 0)
    const response = await fetch('/api/documents/upload', {
      method: 'POST',
      body: formData
    })
    const result = await response.json()
    message.destroy()

    if (result.code === 200) {
      message.success('文档上传成功，正在处理...')
      await loadDocuments()

      // 轮询检查处理状态
      checkDocumentStatus(result.data.documentId)
    } else {
      message.error(result.message || '上传失败')
    }
  } catch (error) {
    message.destroy()
    message.error('上传失败')
  }

  return false
}

// 检查文档处理状态
const checkDocumentStatus = async (documentId) => {
  const interval = setInterval(async () => {
    try {
      const response = await fetch(`/api/documents/${documentId}/status`)
      const result = await response.json()

      if (result.data === 'READY') {
        clearInterval(interval)
        message.success('文档处理完成，可以开始问答了')
        await loadDocuments()
      } else if (result.data === 'ERROR') {
        clearInterval(interval)
        message.error('文档处理失败')
        await loadDocuments()
      }
    } catch (error) {
      clearInterval(interval)
    }
  }, 2000)
}

// 删除文档
const handleDelete = async (id) => {
  try {
    const response = await fetch(`/api/documents/${id}`, {
      method: 'DELETE'
    })
    const result = await response.json()

    if (result.code === 200) {
      message.success('文档已删除')
      await loadDocuments()
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 发送消息
const handleSend = async () => {
  if (!inputMessage.value.trim() || isStreaming.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  isStreaming.value = true

  // 添加用户消息到界面
  messages.value.push({
    id: Date.now(),
    role: 'USER',
    content: userMessage
  })

  // 添加一个临时的助手消息
  const assistantMessageId = Date.now() + 1
  messages.value.push({
    id: assistantMessageId,
    role: 'ASSISTANT',
    content: ''
  })

  await scrollToBottom()

  // 发送 SSE 请求
  try {
    await fetchEventSource('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: userMessage,
        topK: 5
      }),
      onmessage: (event) => {
        const data = event.data

        if (event.event === 'content') {
          // 更新助手消息内容
          const msg = messages.value.find(m => m.id === assistantMessageId)
          if (msg) {
            msg.content += data
          }
          scrollToBottom()
        } else if (event.event === 'done') {
          isStreaming.value = false
        } else if (event.event === 'error') {
          isStreaming.value = false
          message.error('发生错误: ' + data)
        }
      },
      onerror: (error) => {
        isStreaming.value = false
        message.error('连接失败')
        throw error
      }
    })
  } catch (error) {
    isStreaming.value = false
    message.error('发送失败')
  }
}

// 渲染 Markdown
const renderMarkdown = (content) => {
  return md.render(content || '')
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 获取状态颜色
const getStatusColor = (status) => {
  const colors = {
    UPLOADED: 'default',
    PARSING: 'processing',
    VECTORIZING: 'processing',
    READY: 'success',
    ERROR: 'error'
  }
  return colors[status] || 'default'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    UPLOADED: '已上传',
    PARSING: '解析中',
    VECTORIZING: '向量化中',
    READY: '就绪',
    ERROR: '错误'
  }
  return texts[status] || status
}

onMounted(() => {
  loadDocuments()
})
</script>
