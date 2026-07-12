import { IssueStatus, ReportStatus } from '../../mocks/mockData';

type Props = {
  issueStatus: IssueStatus;
  hasVlmResult: boolean;
  reportStatus?: ReportStatus | null;
};

export function IssueStepper({ issueStatus, hasVlmResult, reportStatus }: Props) {
  const steps = [
    { label: '이미지 저장', done: true },
    { label: 'YOLO 분석', done: true },
    { label: '이슈 생성', done: true },
    { label: 'VLM 분석', done: hasVlmResult || ['REAL_FIRE', 'FALSE_ALARM', 'REPORTED', 'CLOSED'].includes(issueStatus) },
    { label: '신고 초안', done: Boolean(reportStatus) },
    { label: '신고 완료', done: reportStatus === 'SENT' || issueStatus === 'REPORTED' },
  ];

  return (
    <ol className="stepper">
      {steps.map((step, index) => (
        <li className={step.done ? 'step done' : 'step'} key={step.label}>
          <span>{index + 1}</span>
          <p>{step.label}</p>
        </li>
      ))}
    </ol>
  );
}
