import { defineStore } from 'pinia'
import { createScene, deleteScene, listScenes, updateScene } from '../api/scene'
import { createSceneForm, type SceneForm, type SceneRecord } from '../types/scene'

export const useSceneStore = defineStore('scene', {
  state: () => ({
    items: [] as SceneRecord[],
    loading: false,
  }),
  actions: {
    async fetchAll() {
      this.loading = true
      try {
        this.items = await listScenes()
      } finally {
        this.loading = false
      }
    },
    async save(id: number | null, payload: SceneForm) {
      if (id === null) {
        await createScene(payload)
      } else {
        await updateScene(id, payload)
      }
      await this.fetchAll()
    },
    async remove(id: number) {
      await deleteScene(id)
      await this.fetchAll()
    },
    createEmptyForm() {
      return createSceneForm()
    },
  },
})
