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


export function reportStatusTone(status?: number) {
  switch (status) {
    case 1:
      return 'warning'
    case 2:
      return 'success'
    case 3:
      return 'danger'
    default:
      return 'default'
  }
}
