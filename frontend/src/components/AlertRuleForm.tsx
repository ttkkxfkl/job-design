import React, { useState } from 'react';
import { alertRuleApi, AlertLevel, CreateAlertRuleRequest, TriggerConditionItem, DependentEventItem } from '../lib/api';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Separator } from './ui/separator';

const levels: AlertLevel[] = ['RED', 'ORANGE', 'YELLOW', 'BLUE'];
const units = ['分钟', '小时', '天'] as const;

export default function AlertRuleForm() {
  const [level, setLevel] = useState<AlertLevel>('RED');
  const [orgScope, setOrgScope] = useState<string>('');
  const [enabled, setEnabled] = useState<boolean>(true);
  const [triggerRelation, setTriggerRelation] = useState<'AND' | 'OR'>('AND');
  const [triggerItems, setTriggerItems] = useState<TriggerConditionItem[]>([
    { operator: '>', source: '业务事件', field: '探水计划首次开始时间', offsetValue: 16, offsetUnit: '小时' },
  ]);
  const [depEvents, setDepEvents] = useState<DependentEventItem[]>([]);
  const [depLogic, setDepLogic] = useState<'AND' | 'OR'>('AND');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string>('');

  const addTriggerItem = () => {
    setTriggerItems([...triggerItems, { operator: '>', source: '业务事件', field: '', offsetValue: undefined, offsetUnit: '小时' }]);
  };

  const removeTriggerItem = (idx: number) => {
    setTriggerItems(triggerItems.filter((_, i) => i !== idx));
  };

  const updateTriggerItem = (idx: number, patch: Partial<TriggerConditionItem>) => {
    setTriggerItems(triggerItems.map((it, i) => (i === idx ? { ...it, ...patch } : it)));
  };

  const addDepEvent = () => {
    setDepEvents([...depEvents, { eventType: '', delayMinutes: 0, required: true }]);
  };

  const removeDepEvent = (idx: number) => {
    setDepEvents(depEvents.filter((_, i) => i !== idx));
  };

  const updateDepEvent = (idx: number, patch: Partial<DependentEventItem>) => {
    setDepEvents(depEvents.map((it, i) => (i === idx ? { ...it, ...patch } : it)));
  };

  const onSave = async () => {
    setSaving(true);
    setMessage('');
    try {
      const payload: CreateAlertRuleRequest = {
        level,
        enabled,
        orgScope: orgScope || undefined,
        triggerCondition: {
          relation: triggerRelation,
          items: triggerItems.filter((it) => it.field.trim() !== ''),
        },
        dependentEvents: depEvents.length
          ? { logicalOperator: depLogic, events: depEvents }
          : undefined,
      };
      await alertRuleApi.create(payload);
      setMessage('保存成功');
      setTriggerItems([]);
      setDepEvents([]);
    } catch (e: any) {
      setMessage(e?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="p-4 space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
        <div>
          <Label>报警级别</Label>
          <select className="w-full border rounded px-2 py-2" value={level} onChange={(e) => setLevel(e.target.value as AlertLevel)}>
            {levels.map((lv) => (
              <option key={lv} value={lv}>{lv}</option>
            ))}
          </select>
        </div>
        <div>
          <Label>适用机构</Label>
          <Input value={orgScope} onChange={(e) => setOrgScope(e.target.value)} placeholder="如：山西省" />
        </div>
        <div className="flex items-center gap-2">
          <Label>是否启用</Label>
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
        </div>
      </div>

      <Separator />

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold">触发时间</h3>
          <div className="flex items-center gap-2">
            <Label>关系</Label>
            <select className="border rounded px-2 py-1" value={triggerRelation} onChange={(e) => setTriggerRelation(e.target.value as 'AND' | 'OR')}>
              <option value="AND">且</option>
              <option value="OR">或</option>
            </select>
            <Button variant="secondary" onClick={addTriggerItem}>新增条件</Button>
          </div>
        </div>

        {triggerItems.map((it, idx) => (
          <div key={idx} className="grid grid-cols-1 md:grid-cols-6 gap-2 items-end">
            <div>
              <Label>比较符</Label>
              <select className="w-full border rounded px-2 py-2" value={it.operator} onChange={(e) => updateTriggerItem(idx, { operator: e.target.value as TriggerConditionItem['operator'] })}>
                {['>', '>=', '<', '<=', '==', '!='].map((op) => (
                  <option key={op} value={op}>{op}</option>
                ))}
              </select>
            </div>
            <div>
              <Label>来源</Label>
              <select className="w-full border rounded px-2 py-2" value={it.source} onChange={(e) => updateTriggerItem(idx, { source: e.target.value as TriggerConditionItem['source'] })}>
                {['业务事件', '系统条件', '运维符'].map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            <div className="md:col-span-2">
              <Label>字段</Label>
              <Input value={it.field} onChange={(e) => updateTriggerItem(idx, { field: e.target.value })} placeholder="如：探水计划首次开始时间" />
            </div>
            <div>
              <Label>偏移值</Label>
              <Input type="number" value={it.offsetValue ?? ''} onChange={(e) => updateTriggerItem(idx, { offsetValue: e.target.value ? Number(e.target.value) : undefined })} />
            </div>
            <div>
              <Label>单位</Label>
              <select className="w-full border rounded px-2 py-2" value={it.offsetUnit} onChange={(e) => updateTriggerItem(idx, { offsetUnit: e.target.value as any })}>
                {units.map((u) => (
                  <option key={u} value={u}>{u}</option>
                ))}
              </select>
            </div>
            <div className="md:col-span-6 flex justify-end">
              <Button variant="destructive" onClick={() => removeTriggerItem(idx)}>删除该行</Button>
            </div>
          </div>
        ))}
      </div>

      <Separator />

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold">依赖事件</h3>
          <div className="flex items-center gap-2">
            <Label>逻辑</Label>
            <select className="border rounded px-2 py-1" value={depLogic} onChange={(e) => setDepLogic(e.target.value as 'AND' | 'OR')}>
              <option value="AND">且</option>
              <option value="OR">或</option>
            </select>
            <Button variant="secondary" onClick={addDepEvent}>新增依赖</Button>
          </div>
        </div>

        {depEvents.map((it, idx) => (
          <div key={idx} className="grid grid-cols-1 md:grid-cols-5 gap-2 items-end">
            <div className="md:col-span-2">
              <Label>事件类型</Label>
              <Input value={it.eventType} onChange={(e) => updateDepEvent(idx, { eventType: e.target.value })} placeholder="如：FIRST_BOREHOLE_START" />
            </div>
            <div>
              <Label>延迟(分钟)</Label>
              <Input type="number" value={it.delayMinutes ?? 0} onChange={(e) => updateDepEvent(idx, { delayMinutes: Number(e.target.value) })} />
            </div>
            <div className="flex items-center gap-2">
              <Label>必需</Label>
              <input type="checkbox" checked={it.required ?? true} onChange={(e) => updateDepEvent(idx, { required: e.target.checked })} />
            </div>
            <div className="flex justify-end">
              <Button variant="destructive" onClick={() => removeDepEvent(idx)}>删除该行</Button>
            </div>
          </div>
        ))}
      </div>

      <Separator />

      <div className="flex items-center justify-end gap-2">
        <Button variant="secondary" onClick={() => { setTriggerItems([]); setDepEvents([]); setMessage('已重置'); }}>重置</Button>
        <Button onClick={onSave} disabled={saving}>{saving ? '保存中...' : '保存'}</Button>
      </div>

      {message && (
        <div className="text-sm text-gray-600">{message}</div>
      )}
    </Card>
  );
}
