/**
 * 单据模块共用的展示工具。
 *
 * 只做格式化，不含任何业务判定 —— 状态能否流转、附件够不够，
 * 一律以后端 `/actions` 与 `/attach-rules` 为准，前端算出来的结论不算数。
 */

/** 一小时的毫秒数 */
const 毫秒每小时 = 3_600_000;

/** 一分钟的毫秒数 */
const 毫秒每分钟 = 60_000;

/**
 * 解析后端时间串（yyyy-MM-dd HH:mm:ss）。
 *
 * 必须把空格换成 T：Safari 与部分老版 WebView 不认空格分隔的时间串，
 * 直接 new Date('2026-11-04 10:00:00') 会得到 Invalid Date。
 *
 * @param 值 时间串
 * @returns Date 对象，解析失败返回 null
 */
function 解析时间(值?: null | string): Date | null {
  if (!值) {
    return null;
  }
  const 结果 = new Date(值.replace(' ', 'T'));
  return Number.isNaN(结果.getTime()) ? null : 结果;
}

/**
 * 计算距截止时间还有多久，用于展示。
 *
 * @param 截止时间 后端返回的 slaDueAt
 * @returns 文本与是否超期，无法解析时返回 null
 */
export function 计算时限(截止时间?: null | string) {
  const 目标 = 解析时间(截止时间);
  if (!目标) {
    return null;
  }
  const 差值 = 目标.getTime() - Date.now();
  const 超期 = 差值 < 0;
  const 绝对值 = Math.abs(差值);
  const 小时 = 绝对值 / 毫秒每小时;

  let 数量: string;
  if (小时 < 1) {
    数量 = `${Math.max(1, Math.round(绝对值 / 毫秒每分钟))} 分钟`;
  } else if (小时 < 48) {
    数量 = `${Math.round(小时)} 小时`;
  } else {
    数量 = `${Math.round(小时 / 24)} 天`;
  }
  return { 文本: 超期 ? `已超期 ${数量}` : `剩余 ${数量}`, 超期 };
}

/**
 * 长文本摘要，用于列表与时间线的单行展示。
 *
 * @param 文本 原文
 * @param 长度 保留字数
 * @returns 截断后的文本
 */
export function 摘要(文本?: null | string, 长度 = 60): string {
  if (!文本) {
    return '';
  }
  const 单行 = 文本.replaceAll(/\s+/g, ' ').trim();
  return 单行.length > 长度 ? `${单行.slice(0, 长度)}…` : 单行;
}

/**
 * 空值占位。详情页大量字段可能为空，统一显示成短横线，避免表格塌陷。
 *
 * @param 值 任意值
 * @returns 展示文本
 */
export function 展示(值?: null | number | string): string {
  if (值 === null || 值 === undefined || 值 === '') {
    return '-';
  }
  return String(值);
}
