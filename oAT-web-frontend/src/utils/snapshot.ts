export function reportStatusText(status?: number) {
  switch (status) {
    case 1:
      return '生成中'
    case 2:
      return '已完成'
    case 3:
      return '失败'
    default:
      return '未生成'
  }
}
