<template>
  <span>
    <template v-for="(seg, i) in segments" :key="i">
      <mark v-if="seg.hit" class="hl">{{ seg.text }}</mark>
      <template v-else>{{ seg.text }}</template>
    </template>
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 命中片段高亮组件：
 * 把文本按查询词切分成 [普通片段, 命中片段, ...]，命中片段渲染成 <mark>。
 */
const props = defineProps({
  text: { type: String, default: '' },
  query: { type: String, default: '' }
})

const segments = computed(() => {
  const text = props.text || ''
  const terms = [...new Set((props.query || '').trim().split(/\s+/))]
    .filter((t) => t.length > 1 && text.toLowerCase().includes(t.toLowerCase()))
  if (!terms.length) return [{ text, hit: false }]

  const regex = new RegExp('(' + terms.map(escapeRegExp).join('|') + ')', 'gi')
  return text
    .split(regex)
    .filter((s) => s !== '')
    .map((s) => ({
      text: s,
      // split 的分隔符就是命中的词本身，判断当前片段是否等于某个查询词即可
      hit: terms.some((t) => s.toLowerCase() === t.toLowerCase())
    }))
})

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
</script>
